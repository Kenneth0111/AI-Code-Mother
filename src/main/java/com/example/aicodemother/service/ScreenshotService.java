package com.example.aicodemother.service;

/**
 * 截图服务接口
 */
public interface ScreenshotService {

    /**
     * 通用的截图服务，可以得到访问地址
     *
     * @param webUrl 要截图的网页URL
     * @return 截图URL
     */
    String generateAndUploadScreenshot(String webUrl);

}
