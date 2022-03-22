package dash.client.fsm.callback;

import dash.client.DashClient;
import dash.client.handler.base.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashClientGetInitSegCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetInitSegCallBack.class);

    private final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    public DashClientGetInitSegCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // GET MPD DONE > PARSE MPD & GET META DATA
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

        long audioSegmentDuration = dashClient.getMpdManager().getAudioSegmentDuration(); // 1000000
        if (audioSegmentDuration > 0) {
            try {
                timeUnit.sleep(audioSegmentDuration);
                logger.trace("[DashClientGetInitSegCallBack({})] [AUDIO] Waiting... ({})", dashClient.getDashUnitId(), audioSegmentDuration);
            } catch (Exception e) {
                //logger.warn("");
            }
        }
        dashClient.sendHttpGetRequest(
                FileManager.concatFilePath(
                        dashClient.getSrcBasePath(),
                        dashClient.getMpdManager().getAudioMediaSegmentName()
                ),
                MessageType.AUDIO
        );

        if (!AppInstance.getInstance().getConfigManager().isAudioOnly()) {
            long videoSegmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
            if (videoSegmentDuration > 0) {
                try {
                    timeUnit.sleep(videoSegmentDuration);
                    logger.trace("[DashClientGetInitSegCallBack({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), videoSegmentDuration);
                } catch (Exception e) {
                    //logger.warn("");
                }
            }
            dashClient.sendHttpGetRequest(
                    FileManager.concatFilePath(
                            dashClient.getSrcBasePath(),
                            dashClient.getMpdManager().getVideoMediaSegmentName()
                    ),
                    MessageType.VIDEO
            );
        }
        ////////////////////////////

        return stateUnit.getCurState();
    }


}