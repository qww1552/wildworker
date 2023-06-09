package com.a304.wildworker.domain.match.strategy;

import com.a304.wildworker.domain.common.League;
import com.a304.wildworker.domain.user.User;

/**
 * 기본 리그 전략 • 리그 ◦ 최상위리그: 100,000 ~ ◦ 상위리그: 10,000 ~ 99,999 ◦ 중위리그: 1,000 ~ 9,999 ◦ 하위리그: 50 ~ 999
 */
public class DefaultLeagueStrategy implements LeagueStrategy {

    public final static long[] UPPER_LIMIT = new long[]{50, 1_000, 10_000, 100_000};

    @Override
    public League getLeague(User user) {
        long balance = user.getBalance();
        League league = League.TOP;

        for (int i = 0; i < UPPER_LIMIT.length; i++) {
            if (balance < UPPER_LIMIT[i]) {
                league = League.fromOrdinary(i);
                break;
            }
        }
        return league;
    }
}
