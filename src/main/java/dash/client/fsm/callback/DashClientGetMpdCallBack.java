package dash.client.fsm.callback;

import dash.client.DashClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tool.parser.mpd.Representation;
import util.fsm.StateManager;
import util.fsm.event.base.CallBack;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.List;

public class DashClientGetMpdCallBack extends CallBack {

    private static final Logger logger = LoggerFactory.getLogger(DashClientGetMpdCallBack.class);

    public DashClientGetMpdCallBack(StateManager stateManager, String name) {
        super(stateManager, name);
    }

    @Override
    public Object callBackFunc(StateUnit stateUnit) {
        if (stateUnit == null) { return null; }

        ////////////////////////////
        // GET MPD DONE > PARSE MPD & GET META DATA
        DashClient dashClient = (DashClient) stateUnit.getData();
        if (dashClient == null) { return null; }

        dashClient.parseMpd();

        List<Representation> representations = dashClient.getRepresentations(DashClient.CONTENT_AUDIO_TYPE);
        if (representations != null && !representations.isEmpty()) {
            // outdoor_market_ambiance_Dolby_init$RepresentationID$.m4s
            String initSegmentName = dashClient.getRawInitializationSegmentName(representations.get(0));
            initSegmentName = initSegmentName.replace(DashClient.REPRESENTATION_ID_POSTFIX, 0 + "");
            String targetAudioInitSegPath = FileManager.concatFilePath(
                    dashClient.getTargetBasePath(),
                    // outdoor_market_ambiance_Dolby_init0.m4s
                    initSegmentName
            );
            dashClient.setTargetAudioInitSegPath(targetAudioInitSegPath);

            dashClient.sendHttpGetRequest(
                    FileManager.concatFilePath(
                            dashClient.getSrcBasePath(),
                            initSegmentName
                    )
            );
        } else {
            logger.warn("[DashClientMpdDoneCallBack] Fail to send http get request for init segment. Representation is not exists. (dashClient={})", dashClient);
        }
        ////////////////////////////

        return stateUnit.getCurState();
    }


}
