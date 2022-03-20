package service.monitor;

import dash.server.DashServer;
import dash.unit.DashUnit;
import dash.unit.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.schedule.ScheduleManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LongSessionRemover extends Job {

    private static final Logger logger = LoggerFactory.getLogger(LongSessionRemover.class);

    private final long limitTime;

    public LongSessionRemover(ScheduleManager scheduleManager, String name, int initialDelay, int interval, TimeUnit timeUnit, int priority, int totalRunCount, boolean isLasted) {
        super(scheduleManager, name, initialDelay, interval, timeUnit, priority, totalRunCount, isLasted);

        limitTime = AppInstance.getInstance().getConfigManager().getLocalSessionLimitTime();
    }

    @Override
    public void run() {
        DashServer dashServer = ServiceManager.getInstance().getDashServer();

        HashMap<String, DashUnit> dashUnitMap = dashServer.getCloneDashMap();
        if (!dashUnitMap.isEmpty()) {
            for (Map.Entry<String, DashUnit> entry : dashUnitMap.entrySet()) {
                if (entry == null) {
                    continue;
                }

                DashUnit dashUnit = entry.getValue();
                if (dashUnit == null) {
                    continue;
                }

                if (!dashUnit.getType().equals(StreamType.DYNAMIC)) { continue; }
                if (dashUnit.getExpires() > 0) { continue; }

                long curTime = System.currentTimeMillis();
                if ((curTime - dashUnit.getInitiationTime()) >= limitTime) {
                    dashUnit.clearMpdPath();
                    dashServer.deleteDashUnit(dashUnit.getId());
                    logger.warn("({}) REMOVED LONG DASH UNIT(DashUnit=\n{})", getName(), dashUnit);
                }
            }
        }
    }
    
}
