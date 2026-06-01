package cn.iocoder.yudao.module.ai.framework.chatgpt;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ChatGptCallerContextHolder {

    private static final ThreadLocal<ChatGptCallerContext> HOLDER = new ThreadLocal<>();

    private ChatGptCallerContextHolder() {
    }

    public static void set(ChatGptCallerContext context) {
        HOLDER.set(context);
    }

    public static ChatGptCallerContext get() {
        return HOLDER.get();
    }

    public static ChatGptCallerContext required() {
        ChatGptCallerContext context = get();
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ChatGPT action caller is not authenticated");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }

}
