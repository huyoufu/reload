#watchservice实现方案的缺陷
```text
该模式是有缺陷的:
      1.只能监控某目录 子目录的变化无法监控
      2.监控之后 文件夹无法改名 操作系统会认为该文件夹被占用
 
      第二个不足可以理解
 
      第一个不足 不行
          因为我们项目下通常有很多子目录 无法监控子目录的变化是我们无法接受的
 
          故spring-boot-devtools 没有采用该方式 而是采用定时器遍历的方式
          虽然定时器遍历有性能损耗,但是这种功能大多是在代码编辑阶段 损耗可以接受
 
          总结建议还是使用spring-boot-devtools的方式来做
```



#watchService基本实现
无论哪个操作系统都是创建一个AbstractPoller 该对象会循环检查文件io时间
例如window下就是如下
```java
//也是调用的一个循环 调用
  while(true) {
             CompletionStatus var1;
             try {
                 var1 = WindowsNativeDispatcher.GetQueuedCompletionStatus(this.port);
             } catch (WindowsException var8) {
                 var8.printStackTrace();
                 return;
             }
```


#关于watchService在windows系统上实现
  
  而WindowsNativeDispatcher.GetQueuedCompletionStatus 调用的是
  GetQueuedCompletionStatus0方法
  接续调用openjdk-jdk8u-jdk8u\jdk\src\windows\native\sun\nio\fs\WindowsNativeDispatcher.c
      
 ```c++
 *      JNIEXPORT void JNICALL
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