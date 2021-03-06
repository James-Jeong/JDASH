package dash.server.handler;

import config.ConfigManager;
import dash.server.DashServer;
import dash.server.handler.definition.HttpMessageHandler;
import dash.server.handler.definition.HttpRequest;
import dash.server.handler.definition.HttpResponse;
import dash.unit.DashUnit;
import io.netty.channel.ChannelHandlerContext;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import stream.AudioService;
import stream.StreamConfigManager;
import util.module.FileManager;

import java.io.File;

public class DashMessageHandler implements HttpMessageHandler {

    ////////////////////////////////////////////////////////////////////////////////
    private static final Logger logger = LoggerFactory.getLogger(DashMessageHandler.class);

    private final ConfigManager configManager = AppInstance.getInstance().getConfigManager();
    private final FileManager fileManager = new FileManager();

    private final String uri;
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    public DashMessageHandler(String uri) {
        this.uri = uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    @Override
    public Object handle(HttpRequest request, HttpResponse response, String originUri, String uriFileName, ChannelHandlerContext ctx, DashUnit dashUnit) {
        if (request == null || uriFileName == null || ctx == null) { return null; }

        // CHECK URI
        String uri = request.getRequest().uri();
        if (!this.uri.equals(uri)) {
            logger.warn("[DashMessageHandler(uri={})] URI is not equal with handler's uri. (uri={})", this.uri, uri);
            return null;
        }

        String uriFileNameWithExtension = fileManager.getFileNameWithExtensionFromUri(uri);
        if (isUriWrong(uriFileNameWithExtension)) {
            logger.warn("[DashMessageHandler(uri={})] Fail to find the mp4 file. (uri={})", this.uri, uri);
            return null;
        }

        // DASH PROCESSING
        return dashProcessing(uriFileName, uriFileNameWithExtension);
    }
    ////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////
    private boolean isUriWrong(String uriFileNameWithExtension) {
        if (uriFileNameWithExtension.contains(".")
                && uriFileNameWithExtension.endsWith(StreamConfigManager.MP4_POSTFIX)) {
            File uriFile = new File(uri);
            return !uriFile.exists() || uriFile.isDirectory();
        }
        return false;
    }

    private String dashProcessing(String uriFileName, String uriFileNameWithExtension) {
        // GENERATE MPD FROM MP4 BY GPAC
        String mp4Path; // Absolute path
        String mpdPath = null; // Absolute path
        try {
            // GET COMMAND & RUN SCRIPT
            if (uriFileNameWithExtension.contains(".")) {
                if (uri.endsWith(StreamConfigManager.MP4_POSTFIX)) {
                    mp4Path = uri;
                    mpdPath = uri.replace(StreamConfigManager.MP4_POSTFIX, StreamConfigManager.DASH_POSTFIX);
                } else if (uri.endsWith(StreamConfigManager.DASH_POSTFIX)) {
                    mp4Path = uri.replace(StreamConfigManager.DASH_POSTFIX, ".mp4");
                    mpdPath = uri;
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. Wrong file extension. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                    return null;
                }

                if (!getMediaStream(mpdPath, mp4Path, uriFileName)) {
                    return null;
                }
            } else {
                mpdPath = fileManager.concatFilePath(uri, uriFileName + StreamConfigManager.DASH_POSTFIX);
            }

            // GET MPD
            DashServer dashServer = ServiceManager.getInstance().getDashServer();
            if (!dashServer.getMpdManager().parseMpd(mpdPath, false)) {
                logger.warn("[DashMessageHandler(uri={})] Fail to parse the mpd. (uri={}, mpdPath={})", this.uri, uri, mpdPath);
                return null;
            }

            // VALIDATE MPD
            if (configManager.isEnableValidation()) {
                if (dashServer.getMpdManager().validate()) {
                    logger.debug("[DashMessageHandler(uri={})] Success to validate the mpd.", this.uri);
                } else {
                    logger.warn("[DashMessageHandler(uri={})] Fail to validate the mpd.", this.uri);
                    return null;
                }
            }

            return dashServer.getMpdManager().writeAsString();
        } catch (Exception e) {
            logger.warn("DashMessageHandler(uri={}).handle.Exception (uri={}, mpdPath={})\n", this.uri, uri, mpdPath, e);
            return null;
        }
    }

    private boolean getMediaStream(String mpdPath, String mp4Path, String uriFileName) {
        File mpdFile = new File(mpdPath);
        if (!mpdFile.exists()) {
            FFmpegFrameRecorder audioFrameRecorder = null;
            FFmpegFrameRecorder videoFrameRecorder = null;
            try (FFmpegFrameGrabber fFmpegFrameGrabber = FFmpegFrameGrabber.createDefault(mp4Path);) {
                if (!configManager.isAudioOnly()) {
                    fFmpegFrameGrabber.setImageWidth(configManager.getRemoteVideoWidth());
                    fFmpegFrameGrabber.setImageHeight(configManager.getRemoteVideoHeight());
                }
                fFmpegFrameGrabber.start();

                // [OUTPUT] FFmpegFrameRecorder
                if (configManager.isAudioOnly()) {
                    audioFrameRecorder = new FFmpegFrameRecorder(
                            mpdPath,
                            AudioService.CHANNEL_NUM
                    );
                    StreamConfigManager.setRemoteStreamAudioOptions(audioFrameRecorder);
                    StreamConfigManager.setDashOptions(audioFrameRecorder,
                            uriFileName,
                            configManager.getSegmentDuration(), 0
                    );
                    audioFrameRecorder.start();
                } else {
                    videoFrameRecorder = new FFmpegFrameRecorder(
                            mpdPath,
                            configManager.getRemoteVideoWidth(), configManager.getRemoteVideoHeight(),
                            AudioService.CHANNEL_NUM
                    );
                    StreamConfigManager.setRemoteStreamVideoOptions(videoFrameRecorder);
                    StreamConfigManager.setRemoteStreamAudioOptions(videoFrameRecorder);
                    StreamConfigManager.setDashOptions(videoFrameRecorder,
                            uriFileName,
                            configManager.getSegmentDuration(), 0
                    );
                    videoFrameRecorder.start();
                }

                long startTime = 0;
                Frame capturedFrame;
                while (true) {
                    if (configManager.isAudioOnly()) {
                        capturedFrame = fFmpegFrameGrabber.grabSamples();
                    } else {
                        capturedFrame = fFmpegFrameGrabber.grab();
                    }
                    if (capturedFrame == null) {
                        break;
                    }

                    if (configManager.isAudioOnly() && audioFrameRecorder != null) {
                        // AUDIO DATA ONLY
                        if (capturedFrame.samples != null && capturedFrame.samples.length > 0) {
                            audioFrameRecorder.record(capturedFrame);
                        }
                    } else if (videoFrameRecorder != null) {
                        // Check for AV drift
                        if (startTime == 0) {
                            startTime = System.currentTimeMillis();
                        }
                        long curTimeStamp = 1000 * (System.currentTimeMillis() - startTime);
                        if (curTimeStamp > videoFrameRecorder.getTimestamp()) { // Lip-flap correction
                            videoFrameRecorder.setTimestamp(curTimeStamp);
                        }

                        videoFrameRecorder.record(capturedFrame);
                    }
                }
            } catch (Exception e) {
                // ignore
                logger.warn("[DashMessageHandler(uri={})] run.Exception", uri, e);
                return false;
            } finally {
                try {
                    if (videoFrameRecorder != null) {
                        videoFrameRecorder.stop();
                        videoFrameRecorder.release();
                    }

                    if (audioFrameRecorder != null) {
                        audioFrameRecorder.stop();
                        audioFrameRecorder.release();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }

            mpdFile = new File(mpdPath);
            if (!mpdFile.exists()) {
                logger.warn("[DashMessageHandler(uri={})] Fail to generate the mpd file. MPD file is not exists. (mpdPath={})", uri, mpdPath);
                return false;
            }
        }

        return true;
    }

    public String getUri() {
        return uri;
    }
    ////////////////////////////////////////////////////////////////////////////////

}
