package com.jk1123.watchservice;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.List;

/**
 *
 *  请求注入该模式是有缺陷的:
 *      1.只能监控某目录 子目录的变化无法监控
 *      2.监控之后 文件夹无法改名 操作系统会认为该文件夹被占用
 *
 *      第二个不足可以理解
 *
 *      第一个不足 不行
 *          因为我们项目下通常有很多子目录 无法监控子目录的变化是我们无法接受的
 *
 *          故spring-boot-devtools 没有采用该方式 而是采用定时器遍历的方式
 *          虽然定时器遍历有性能损耗,但是这种功能大多是在代码编辑阶段 损耗可以接受
 *
 *          总结建议还是使用spring-boot-devtools的方式来做
 *
 *      而且windowService 也是调用的一个循环 调用
 *      while(true) {
 *                 CompletionStatus var1;
 *                 try {
 *                     var1 = WindowsNativeDispatcher.GetQueuedCompletionStatus(this.port);
 *                 } catch (WindowsException var8) {
 *                     var8.printStackTrace();
 *                     return;
 *                 }
 *      而WindowsNativeDispatcher.GetQueuedCompletionStatus 调用的是
 *      GetQueuedCompletionStatus0方法
 *      接续调用openjdk-jdk8u-jdk8u\jdk\src\windows\native\sun\nio\fs\WindowsNativeDispatcher.c
 *      JNIEXPORT void JNICALL
 *
 * Java_sun_nio_fs_WindowsNativeDispatcher_GetQueuedCompletionStatus0(JNIEnv* env, jclass this,
 *     jlong completionPort, jobject obj)
 * {
 *     DWORD bytesTransferred;
 *     ULONG_PTR completionKey;
 *     OVERLAPPED *lpOverlapped;
 *     BOOL res;
 *
 *     res = GetQueuedCompletionStatus((HANDLE)jlong_to_ptr(completionPort),
 *                                   &bytesTransferred,
 *                                   &completionKey,
 *                                   &lpOverlapped,
 *                                   INFINITE);
 *     if (res == 0 && lpOverlapped == NULL) {
 *         throwWindowsException(env, GetLastError());
 *     } else {
 *         DWORD ioResult = (res == 0) ? GetLastError() : 0;
 *         (*env)->SetIntField(env, obj, completionStatus_error, ioResult);
 *         (*env)->SetIntField(env, obj, completionStatus_bytesTransferred,
 *             (jint)bytesTransferred);
 *         (*env)->SetLongField(env, obj, completionStatus_completionKey,
 *             (jlong)completionKey);
 *     }
 * }
 *      最终调用了 window操作系统的 GetQueuedCompletionStatus 函数
 *
 *
 *
 *
 */
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
