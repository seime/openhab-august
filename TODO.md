2023-01-10 05:42:50.022 [WARN ] [t.internal.handler.AugustLockHandler] - XX Error
contacting lock
org.openhab.binding.august.internal.RestCommunicationException: Error sending request to server. Server responded with
531
and payload {"code":31,"message":"Mechanical Position"}
at org.openhab.binding.august.internal.ApiBridge.sendRequestInternal(ApiBridge.java:132) ~[bundleFile:?]
at org.openhab.binding.august.internal.ApiBridge.sendRequest(ApiBridge.java:97) ~[bundleFile:?]
at org.openhab.binding.august.internal.handler.AugustLockHandler.handleLockStateCommand(AugustLockHandler.java:

196) [bundleFile:?]
     at org.openhab.binding.august.internal.handler.AugustLockHandler.handleCommandInternal(AugustLockHandler.java:
167) [bundleFile:?]
     at org.openhab.binding.august.internal.handler.AugustLockHandler.handleCommand(AugustLockHandler.java:
158) [bundleFile:?]
     at jdk.internal.reflect.GeneratedMethodAccessor154.invoke(Unknown Source) ~[?:?]
     at jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[?:?]
     at java.lang.reflect.Method.invoke(Method.java:566) ~[?:?]
     at org.openhab.core.internal.common.AbstractInvocationHandler.invokeDirect(AbstractInvocationHandler.java:
154) [bundleFile:?]
     at org.openhab.core.internal.common.InvocationHandlerSync.invoke(InvocationHandlerSync.java:59) [bundleFile:?]
     at com.sun.proxy.$Proxy8590.handleCommand(Unknown Source) [?:?]
     at org.openhab.core.thing.internal.profiles.ProfileCallbackImpl.handleCommand(ProfileCallbackImpl.java:
80) [bundleFile:?]
    at org.openhab.core.thing.internal.profiles.SystemDefaultProfile.onCommandFromItem(SystemDefaultProfile.java:
48) [bundleFile:?]
    at jdk.internal.reflect.GeneratedMethodAccessor153.invoke(Unknown Source) ~[?:?]
    at jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43) ~[?:?]
    at java.lang.reflect.Method.invoke(Method.java:566) ~[?:?]
    at org.openhab.core.internal.common.AbstractInvocationHandler.invokeDirect(AbstractInvocationHandler.java:
154) [bundleFile:?]
     at org.openhab.core.internal.common.Invocation.call(Invocation.java:52) [bundleFile:?]
     at java.util.concurrent.FutureTask.run(FutureTask.java:264) [?:?]
     at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128) [?:?]

