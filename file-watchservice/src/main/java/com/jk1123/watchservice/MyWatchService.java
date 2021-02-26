package com.jk1123.watchservice;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;

public class MyWatchService {
    public static void main(String[] args) throws IOException, URISyntaxException {

        //创建一个watchService 底层是创建了 WindowsWatchService
        WatchService watchService= FileSystems.getDefault()
                                            .newWatchService();

        Paths.get("D:/logs").register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.OVERFLOW);

        while (true){

            try {
                WatchKey wk = watchService.take();


                List<WatchEvent<?>> watchEvents = wk.pollEvents();
                for (WatchEvent we:watchEvents){
                    /**
                     * 一个特殊事件，表示事件可能已丢失或丢弃。
                     * 此事件的context是特定于实现的，可能是null 。 事件count可能大于1 。
                     */
                    if (we.kind().equals(StandardWatchEventKinds.OVERFLOW)){
                        System.out.println(we.context()+"触发OVERFLOW");
                    }
                    /**
                     * 目录条目已创建。
                     * 为此事件注册目录时，如果发现在目录中创建了条目或重命名为目录，则会将WatchKey排入队列。 此事件的事件count始终为1 。
                     */
                    if (we.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)){
                        System.out.println(we.context()+"触发ENTRY_CREATE");
                    }
                    /**
                     * 目录条目已修改。
                     * 为此事件注册目录时，如果发现目录中的条目已被修改，则WatchKey将排队。 此活动的事件count是1或更高。
                     */
                    if (we.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)){
                        System.out.println(we.context()+"触发ENTRY_MODIFY");
                    }
                    /**
                     * 目录条目已删除。
                     * 当为此事件注册目录时，如果观察到条目被删除或重命名为目录，则WatchKey将排队。 此活动的事件count始终为1 。
                     */
                    if (we.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)){
                        System.out.println(we.context()+"触发ENTRY_DELETE");
                    }


                }




                wk.reset();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }



        }



    }
}
