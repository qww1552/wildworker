package com.a304.wildworker.event.handler;

import com.a304.wildworker.event.PoolChangeEvent;
import com.a304.wildworker.event.common.EventPublish;
import com.a304.wildworker.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PoolChangeEventHandler {

    private final MatchService matchService;

    @EventPublish
    @EventListener
    public void makeMatch(PoolChangeEvent event) {
        log.info("PoolChangeEvent raise: {}", event);

        matchService.makeMatch(event.getActiveStation());
    }

}
