package com.bolingcavalry.grabpush.camera;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.IplImage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.presets.opencv_objdetect;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author will
 * @email zq2599@gmail.com
 * @date 2021/11/19 8:07 上午
 * @description 功能介绍
 */
@Slf4j
public abstract class AbstractCameraApplication {
    /**
     * 摄像头序号，如果只有一个摄像头，那就是0
     */
    protected static final int CAMERA_INDEX = 0;

    /**
     * 帧抓取器
     */
    protected FrameGrabber grabber;

    /**
     * 输出帧率
     */
    @Setter
    @Getter
    private double frameRate;

    /**
     * 摄像头视频的宽
     */
    @Setter
    @Getter
    private int cameraImageWidth;

    /**
     * 摄像头视频的高
     */
    @Setter
    @Getter
    private int cameraImageHeight;

    /**
     * 转换器
     */
    private OpenCVFrameConverter.ToIplImage openCVConverter = new OpenCVFrameConverter.ToIplImage();

    public AbstractCameraApplication(double frameRate) {
        this.frameRate = frameRate;
    }

    /**
     * 实例化、初始化输出操作相关资源
     */
    protected abstract void initOutput() throws Exception;

    /**
     * 输出
     */
    protected abstract void output(Frame frame) throws Exception;

    /**
     * 释放输出操作相关的资源
     */
    protected abstract void releaseOutputResource() throws Exception;

    /**
     * 两帧之间的间隔时间
     * @return
     */
    protected int getInterval() {
        // 假设一秒钟15帧，那么两帧间隔就是(1000/15)毫秒
        return (int)(1000/ frameRate);
    }

    /**
     * 初始化帧抓取器
     * @throws Exception
     */
    protected void initGrabber() throws Exception {
        grabber = new OpenCVFrameGrabber(CAMERA_INDEX);

        // 开启抓取器
        grabber.start();

        // 宽度和高度都来自抓取器
        cameraImageWidth = grabber.getImageWidth();
        cameraImageHeight = grabber.getImageHeight();
    }

    /**
     * 预览和输出
     * @param grabSeconds 持续时长
     * @throws Exception
     */
    private void grabAndOutput(int grabSeconds) throws Exception {
        // 添加水印时用到的时间工具
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        long endTime = System.currentTimeMillis() + 1000*grabSeconds;

        // 两帧输出之间的间隔时间，默认是1000除以帧率，子类可酌情修改
        int interVal = getInterval();

        org.bytedeco.opencv.opencv_core.Point point = new org.bytedeco.opencv.opencv_core.Point(15, 35);

        Frame captureFrame;
        Mat mat;

        // 超过指定时间就结束循环
        while (System.currentTimeMillis()<endTime && (captureFrame = grabber.grab()) != null) {
            captureFrame = grabber.grab();

            if (null==captureFrame) {
                log.error("帧对象为空");
                break;
            }

            mat = openCVConverter.convertToMat(captureFrame);

            // 在图片上添加水印
            opencv_imgproc.putText(mat,
                    simpleDateFormat.format(new Date()),
                    point,
                    opencv_imgproc.CV_FONT_VECTOR0,
                    0.8,
                    new Scalar(0, 200, 255, 0),
                    1,
                    0,
                    false);

            // 子类输出
            output(openCVConverter.convert(mat));

            // 适当间隔，让肉感感受不到闪屏即可
            Thread.sleep((int)interVal);
        }

        log.info("输出结束");
    }

    /**
     * 释放所有资源
     * @throws Exception
     */
    private void safeRelease() {
        try {
            // 子类需要释放的资源
            releaseOutputResource();
        } catch (Exception exception) {
            log.error("do releaseOutputResource error", exception);
        }

        if (null!=grabber) {
            try {
                grabber.close();
            } catch (Exception exception) {
                log.error("close grabber error", exception);
            }
        }
    }

    /**
     * 整合了所有初始化操作
     * @throws Exception
     */
    private void init() throws Exception {
        long startTime = System.currentTimeMillis();

        // 设置ffmepg日志级别
        avutil.av_log_set_level(avutil.AV_LOG_INFO);
        FFmpegLogCallback.set();

        // 加载检测
        Loader.load(opencv_objdetect.class);

        // 实例化、初始化帧抓取器
        initGrabber();

        // 实例化、初始化窗口
        initOutput();

        log.info("初始化完成，耗时[{}]毫秒，帧率[{}]，图像宽度[{}]，图像高度[{}]",
                System.currentTimeMillis()-startTime,
                frameRate,
                cameraImageWidth,
                cameraImageHeight);
    }

    /**
     * 执行抓取和输出的操作
     */
    public void action(int grabSeconds) {
        try {
            // 初始化操作
            init();
            // 持续拉取和推送
            grabAndOutput(grabSeconds);
        } catch (Exception exception) {
            log.error("execute action error", exception);
        } finally {
            // 无论如何都要释放资源
            safeRelease();
        }
    }

}
