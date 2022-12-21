

import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.file.service.FileStorageService;
import com.heima.wemedia.WemediaApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Map;

@SpringBootTest(classes = WemediaApplication.class)
@RunWith(SpringRunner.class)
public class AliyunTest {

    @Autowired
    private GreenTextScan greenTextScan;

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    public void testScanText() throws Exception  {
        Map map = greenTextScan.greeTextScan("我是一个好人,病毒，嫖娼，色淫");
        System.out.println(map);

    }

    @Test
    public void testScanImage() throws Exception {
        byte[] bytes = fileStorageService.downLoadFile("http://43.140.194.170:9000/leadnews/2022/12/10/f3593d58dead404bbab6cfe6effc2ee2.jpg");
        Map map = greenImageScan.imageScan(Arrays.asList(bytes));
        System.out.println(map);
    }
}