package com.cmcu.itstudy.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    // Store all active emitters for broadcasting to all clients
    private final Set<SseEmitter> allEmitters = ConcurrentHashMap.newKeySet();

    // Map userId -> set of active emitters for user-specific notifications (supports multiple tabs)
    private final Map<UUID, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        allEmitters.add(emitter);

        if (userId != null) {
            userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        }

        Runnable removeEmitter = () -> {
            allEmitters.remove(emitter);
            if (userId != null) {
                Set<SseEmitter> set = userEmitters.get(userId);
                if (set != null) {
                    set.remove(emitter);
                    if (set.isEmpty()) {
                        userEmitters.remove(userId);
                    }
                }
            }
        };

        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError((e) -> removeEmitter.run());

        // Send initial connect ping event
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected successfully"));
        } catch (IOException e) {
            removeEmitter.run();
        }

        return emitter;
    }

    public void pushEvent(UUID recipientId, String eventName, Object data) {
        if (recipientId == null) return;
        Set<SseEmitter> set = userEmitters.get(recipientId);
        if (set != null && !set.isEmpty()) {
            for (SseEmitter emitter : set) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(data));
                } catch (Exception e) {
                    set.remove(emitter);
                }
            }
        }
    }

    public void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : allEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                allEmitters.remove(emitter);
            }
        }
    }
}
