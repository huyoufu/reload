#关于watchService在windows系统上实现
```html
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
```

#关于watchService在linux系统实现
```html
而linux操作则是调用了 inotify实现的
具体参考
openjdk-jdk8u-jdk8u\jdk\src\solaris\native\sun\nio\fs\LinuxWatchService.c
```
具体实现
```c++
JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxWatchService_eventSize(JNIEnv *env, jclass clazz)
{
    return (jint)sizeof(struct inotify_event);
}

JNIEXPORT jintArray JNICALL
Java_sun_nio_fs_LinuxWatchService_eventOffsets(JNIEnv *env, jclass clazz)
{
    jintArray result = (*env)->NewIntArray(env, 5);
    if (result != NULL) {
        jint arr[5];
        arr[0] = (jint)offsetof(struct inotify_event, wd);
        arr[1] = (jint)offsetof(struct inotify_event, mask);
        arr[2] = (jint)offsetof(struct inotify_event, cookie);
        arr[3] = (jint)offsetof(struct inotify_event, len);
        arr[4] = (jint)offsetof(struct inotify_event, name);
        (*env)->SetIntArrayRegion(env, result, 0, 5, arr);
    }
    return result;
}
JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxWatchService_inotifyInit
    (JNIEnv* env, jclass clazz)
{
    //初始化inotify
    int ifd = inotify_init();
    if (ifd == -1) {
        throwUnixException(env, errno);
    }
    return (jint)ifd;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxWatchService_inotifyAddWatch
    (JNIEnv* env, jclass clazz, jint fd, jlong address, jint mask)
{
    int wfd = -1;
    const char* path = (const char*)jlong_to_ptr(address);
    //添加路径
    wfd = inotify_add_watch((int)fd, path, mask);
    if (wfd == -1) {
        throwUnixException(env, errno);
    }
    return (jint)wfd;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxWatchService_inotifyRmWatch
    (JNIEnv* env, jclass clazz, jint fd, jint wd)
{
    //移除观察
    int err = inotify_rm_watch((int)fd, (int)wd);
    if (err == -1)
        throwUnixException(env, errno);
}
JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxWatchService_configureBlocking
    (JNIEnv* env, jclass clazz, jint fd, jboolean blocking)
{
    int flags = fcntl(fd, F_GETFL);

    if ((blocking == JNI_FALSE) && !(flags & O_NONBLOCK))
        fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    else if ((blocking == JNI_TRUE) && (flags & O_NONBLOCK))
        fcntl(fd, F_SETFL, flags & ~O_NONBLOCK);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxWatchService_socketpair
    (JNIEnv* env, jclass clazz, jintArray sv)
{
    int sp[2];
    if (socketpair(PF_UNIX, SOCK_STREAM, 0, sp) == -1) {
        throwUnixException(env, errno);
    } else {
        jint res[2];
        res[0] = (jint)sp[0];
        res[1] = (jint)sp[1];
        (*env)->SetIntArrayRegion(env, sv, 0, 2, &res[0]);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxWatchService_poll
    (JNIEnv* env, jclass clazz, jint fd1, jint fd2)
{
    struct pollfd ufds[2];
    int n;

    ufds[0].fd = fd1;
    ufds[0].events = POLLIN;
    ufds[1].fd = fd2;
    ufds[1].events = POLLIN;

    n = poll(&ufds[0], 2, -1);
    if (n == -1) {
        if (errno == EINTR) {
            n = 0;
        } else {
            throwUnixException(env, errno);
        }
     }
    return (jint)n;
}

```