package com.heima.schedule.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Slf4j
@Transactional
public class TaskServiceImpl implements TaskService {

    /**
     * 添加任务
     *
     * @param task 任务对象
     * @return 任务id
     */
    @Override
    public long addTask(Task task) {
        //添加到数据库
        addTaskToDb(task);
        //添加到redis
        addTaskToRedis(task);

        return task.getTaskId();
    }

    /**
     * 取消任务
     *
     * @param taskId 任务id
     * @return 取消结果
     */
    @Override
    public boolean cancelTask(long taskId) {
        //先删库，再删redis，防止先删redis，又同步到了redis中
        //删除数据库任务，更新日志
        Task task=updateDb(taskId,ScheduleConstants.CANCELLED);
        if(task!=null){
            removeTaskFormCache(task);
            return true;
        }

        //删除redis数据

        return false;
    }

    /**
     * 按照类型和优先级来拉取任务
     *
     * @param type
     * @param priority
     * @return
     */
    @Override
    public Task poll(int type, int priority) {
        Task task=null;
        try{
            String key = type+"_"+priority;
            String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if(StringUtils.isNotBlank(task_json)){
                System.out.println(task_json);
                System.out.println(JSON.class);
                log.error("--------------------------------------");
                task = JSON.parseObject(task_json, Task.class);}
            if(task!=null){
                updateDb(task.getTaskId(),ScheduleConstants.EXECUTED);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("poll task exception");
        }
        return task;
    }

    private void removeTaskFormCache(Task task) {
        //先删zset 防止删了list又同步过去
        String key = task.getTaskType()+"_"+task.getPriority();
        cacheService.zRemove(ScheduleConstants.FUTURE + key, JSON.toJSONString(task));
        cacheService.lRemove(ScheduleConstants.TOPIC+key,0,JSON.toJSONString(task));
    }

    private Task updateDb(long taskId, int status) {
        Task task=null;
        try{
            taskinfoMapper.deleteById(taskId);
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);
            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs,task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());


        }catch (Exception e){
            //出现异常，手动回滚，记录错误日志
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("删除任务或更新失败，taskid={}",taskId);
            return null;
        }
        return task;
    }

    @Autowired
    private CacheService cacheService;
    private void addTaskToRedis(Task task) {
        String key=task.getTaskType()+"_"+task.getPriority();

        Calendar calendar=Calendar.getInstance();
        calendar.add(Calendar.MINUTE,5);
        //当前时间5分钟后
        long nextScheduleTime=calendar.getTimeInMillis();
        if(task.getExecuteTime()<=System.currentTimeMillis()){
            //存list 立即消费
            cacheService.lLeftPush(ScheduleConstants.TOPIC+key, JSON.toJSONString(task));
        }else if(task.getExecuteTime()<=nextScheduleTime){
            cacheService.zAdd(ScheduleConstants.FUTURE+key,JSON.toJSONString(task),task.getExecuteTime());
        }

    }

    @Autowired
    private TaskinfoMapper taskinfoMapper;
    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    private boolean addTaskToDb(Task task) {
        Taskinfo taskinfo=new Taskinfo();
        BeanUtils.copyProperties(task,taskinfo);
        taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
        taskinfoMapper.insert(taskinfo);
        //插入后会回传id
        task.setTaskId(taskinfo.getTaskId());
        TaskinfoLogs taskinfoLogs=new TaskinfoLogs();
        BeanUtils.copyProperties(taskinfo,taskinfoLogs);
        taskinfoLogs.setVersion(1);
        taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
        taskinfoLogsMapper.insert(taskinfoLogs);

        return true;
    }

    /**
     * 从 zset --> list
     */
@Scheduled(cron = "0 */1 * * * ?")
    public void refresh(){
    String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
    if(StringUtils.isNotBlank(token)){
        System.out.println(System.currentTimeMillis() / 1000 + "执行了定时任务");
        //从zset 中获取key集合
        Set<String> futureKeys=cacheService.scan(ScheduleConstants.FUTURE+"*");
        for (String futureKey : futureKeys) {
            //每一个futureKey-->zset集合 将这些全部同步到list中
            String topicKey=ScheduleConstants.TOPIC+futureKey.split(ScheduleConstants.FUTURE)[1];
            Set<String> tasks=cacheService.zRangeByScore(futureKey,0,System.currentTimeMillis());
            //同步数据
            if(!tasks.isEmpty()){
                cacheService.refreshWithPipeline(futureKey,topicKey,tasks);
                log.info("成功的将"+futureKey+"刷新到了"+topicKey);
            }
        }
    }
}
@Scheduled(cron = "0 */5 * * * ?")
@PostConstruct
public void reloadData(){
    log.info("数据库数据同步到缓存");
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.MINUTE, 5);
    //calendar.getTime() 当前时间+5分钟后的时间
    //在task中查看小于未来5分钟的任务 且并没有加入到队列中的任务 （状态根据日志表）
    List<Taskinfo> allTasks = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime,calendar.getTime()));
    if(allTasks!=null && allTasks.size()>0){
        for (Taskinfo taskinfo : allTasks) {
            Task task=new Task();
            BeanUtils.copyProperties(taskinfo,task);
            task.setExecuteTime(taskinfo.getExecuteTime().getTime());
            addTaskToRedis(task);
        }
    }
}
//开玩笑的解决方案
    private void clearCache(){
        // 删除缓存中未来数据集合和当前消费者队列的所有key
        Set<String> futurekeys = cacheService.scan(ScheduleConstants.FUTURE + "*");// future_
        Set<String> topickeys = cacheService.scan(ScheduleConstants.TOPIC + "*");// topic_
        cacheService.delete(futurekeys);
        cacheService.delete(topickeys);
    }

}
