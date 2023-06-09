package com.a304.wildworker.service;

import com.a304.wildworker.domain.activestation.ActiveStation;
import com.a304.wildworker.domain.activeuser.ActiveUser;
import com.a304.wildworker.domain.match.Match;
import com.a304.wildworker.event.common.EventPublish;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class EventService {

    @EventPublish
    public void subUnsubStation(ActiveUser activeUser, Boolean subscribed) {
        activeUser.setSubscribed(subscribed);
    }

    @EventPublish
    public void insertToStationPool(long userId, ActiveStation activeStation) {
        activeStation.insertToPool(userId);
    }

    @EventPublish
    public void endMatchSelectLimitTime(Match match) {
        match.endSelectingProgress();
    }

    @EventPublish
    public void endMiniGameProgressLimitTime(Match match) {
        match.endMiniGameProgress();
    }


}
