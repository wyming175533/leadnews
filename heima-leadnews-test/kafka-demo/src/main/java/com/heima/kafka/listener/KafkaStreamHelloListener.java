package com.heima.kafka.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;

@Configuration
@Slf4j
public class KafkaStreamHelloListener {
    @Bean
    public KStream<String,String> kStream(StreamsBuilder streamsBuilder){
        //创建kstream对象 同时指定topic
        KStream<String,String> stream=streamsBuilder.stream("itcast-topic-input");
        //将一串消息转为迭代器对象，看作集合即可
        stream.flatMapValues(new ValueMapper<String, Iterable<?>>() {
            @Override
            public Iterable<?> apply(String value) {//hello hello hello
                return Arrays.asList(value.split(" "));
            }
        })      //根据value聚合分组
                .groupBy((key,value)->value)
                //聚合计算时间间隔
                .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))
                //处理->统计个数
                .count()
                .toStream()
                .map((key,value)->{
                    return new KeyValue<>(key.key().getClass(),value.toString());
                }).//发送到消费者
                to("itcast-topic-out");
            return stream;
    }
    @Bean
    public StreamsBuilder streamsBuilder(){
        return new StreamsBuilder();
    }
}
