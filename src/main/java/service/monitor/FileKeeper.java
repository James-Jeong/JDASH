package service.monitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;
import service.ServiceManager;
import service.scheduler.job.Job;
import service.scheduler.job.JobContainer;

import java.nio.file.*;
import java.util.List;

public class FileKeeper extends JobContainer {

    private static final Logger logger = LoggerFactory.getLogger(FileKeeper.class);

    private transient WatchService watchService = null; // 파일 변경 감지 모니터 서비스 > 특정 경로 전체 감지 > 특정 파일은 안됨
    private Path mediaListPath = null;

    public FileKeeper(Job fileKeepJob) {
        setJob(fileKeepJob);
    }

    public boolean init() {
        try {
            watchService = FileSystems.getDefault().newWatchService();

            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            Path basePath = Paths.get(configManager.getMediaListPath()).getParent();
            basePath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.OVERFLOW
            );
            mediaListPath = Paths.get(configManager.getMediaListPath());

            logger.debug("[FileKeeper] Success to start the watch service. (basePath={})", basePath);
            return true;
        } catch (Exception e) {
            logger.warn("[FileKeeper] Fail to start the watch service.", e);
            return false;
        }
    }

    public void start() {
        getJob().setRunnable(() -> {
            try {
                //////////////////////////////
                // WatchService 가 넘겨줄 이벤트
                WatchKey watchKey;
                try {
                    watchKey = watchService.take();
                    if (watchKey == null) {
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("[FileKeeper] Fail to take the watch event.", e);
                    return;
                }
                //////////////////////////////

                //////////////////////////////
                // 이벤트 처리
                List<WatchEvent<?>> events = watchKey.pollEvents();
                for (WatchEvent<?> event : events) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path curPath = (Path) event.context();
                    if (curPath.equals(mediaListPath.getFileName())) {
                        if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                            logger.debug("[FileKeeper] [{}] entry created.", curPath);
                        } else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                            logger.debug("[FileKeeper] [{}] entry deleted.", curPath);
                        } else if(kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                            logger.debug("[FileKeeper] [{}] entry modified.", curPath);
                        } else if(kind.equals(StandardWatchEventKinds.OVERFLOW)) {
                            logger.warn("[FileKeeper] [{}] A special event to indicate that events may have been lost or discarded.", curPath);
                            continue;
                        } else {
                            logger.warn("[FileKeeper] [{}] Unknown event is occurred.", curPath);
                            continue;
                        }

                        ServiceManager.getInstance().getDashServer().loadMediaUriList();
                    }
                }
                //////////////////////////////

                //////////////////////////////
                // 이벤트 초기화 > reset() 함수 호출 안하면 다음 이벤트 받을 수 없음
                if (!watchKey.reset()) {
                    try {
                        watchService.close();
                    } catch (Exception e) {
                        logger.warn("[FileKeeper] Fail to close the watch service.", e);
                    }
                }
                //////////////////////////////
            } catch (Exception e) {
                logger.warn("[FileKeeper] Fail to run the watch service.", e);
            }
        });
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}
