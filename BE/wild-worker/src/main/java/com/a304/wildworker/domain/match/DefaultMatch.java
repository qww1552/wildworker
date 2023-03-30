package com.a304.wildworker.domain.match;

import com.a304.wildworker.domain.common.MiniGameType;
import com.a304.wildworker.domain.user.User;
import java.util.List;
import lombok.Getter;

/*
 * DefaultMatch
 * • 랜덤 게임
 * • 리그별 고정 배팅 금액
 *      ◦ 최상위 : 20,000원 ◦ 상위 : 2,000원 ◦ 중위 :  200원 ◦ 하위 :  20원
 * • 리그별 고정 도망비
 *      ◦ 최상위 : 5,000원 ◦ 상위 : 500원 ◦ 중위 :  50원 ◦ 하위 :  5원
 */
@Getter
public class DefaultMatch extends Match {

    public static final int timeLimitSec = 7;
    private static final int defaultCost = 20;
    private static final int defaultRunCost = 5;
    private final int league;

    public DefaultMatch(User user1, User user2, int league) {
        super(List.of(user1, user2), MiniGameType.random());        //게임 랜덤으로 정함
        this.league = league;
    }

    public int getCost() {
        return defaultCost * (int) (Math.pow(10, league));
    }

    public int getRunCost() {
        return defaultRunCost * (int) (Math.pow(10, league));
    }

}
