package com.a304.wildworker.service;

import com.a304.wildworker.domain.common.CharacterType;
import com.a304.wildworker.domain.common.TitleCode;
import com.a304.wildworker.domain.common.TitleShowType;
import com.a304.wildworker.domain.dominator.DominatorLog;
import com.a304.wildworker.domain.dominator.DominatorLogRepository;
import com.a304.wildworker.domain.station.Station;
import com.a304.wildworker.domain.station.StationRepository;
import com.a304.wildworker.domain.system.SystemData;
import com.a304.wildworker.domain.title.Title;
import com.a304.wildworker.domain.title.TitleRepository;
import com.a304.wildworker.domain.transaction.TransactionLog;
import com.a304.wildworker.domain.transaction.TransactionLogRepository;
import com.a304.wildworker.domain.user.User;
import com.a304.wildworker.domain.user.UserRepository;
import com.a304.wildworker.dto.request.ChangeUserInfoRequest;
import com.a304.wildworker.dto.response.*;
import com.a304.wildworker.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final DominatorLogRepository dominatorLogRepository;
    private final TitleRepository titleRepository;
    private final StationRepository stationRepository;
    private final SystemData systemData;
    private final TransactionLogRepository transactionLogRepository;
    private final TitleService titleService;

    public UserResponse getUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);
        TitleDto titleDto = getTitleDto(user);

        return UserResponse.of(user, titleDto);
    }

    public TitleDto getTitleDto(User user) {
        TitleDto titleDto = null;

        // 지배자 칭호인 경우
        if (user.getTitleShowType() == TitleShowType.DOMINATOR) {
            if (user.getTitleId().equals(TitleCode.NONE.getId())) {
                titleDto = TitleDto.of(getTitleOrElseThrow(user.getTitleId()));
            } else {
                titleDto = TitleDto.of(getStationOrElseThrow(user.getTitleId()));
            }
        }
        // 일반 칭호인 경우
        else if (user.getTitleShowType() == TitleShowType.TITLE) {
            titleDto = TitleDto.of(getTitleOrElseThrow(user.getTitleId()));
        }

        return titleDto;
    }

    /* 회원정보 수정 */
    @Transactional
    public void changeUserInfo(Long userId, ChangeUserInfoRequest request) {
        User user = getUserOrElseThrow(userId);

        // 닉네임 변경
        if (request.getName() != null) {
            // 중복 확인
            if (!userRepository.existsByNameAndIdNot(request.getName(), userId)) {
                user.setName(request.getName());
            } else {
                throw new DuplicatedNameException();
            }
        }

        // 칭호 종류 변경
        if (request.getTitleType() != null) {
            user.setTitleShowType(TitleShowType.fromOrdinary(request.getTitleType()));

            // 지배자로 설정한 경우
            if (request.getTitleType() == TitleShowType.DOMINATOR.ordinal()) {
                Station mainStation = getMostExpensiveStation(userId);

                // 지배하고 있는 가장 비싼 역으로 설정
                if (mainStation != null) {
                    user.setTitleId(mainStation.getId());
                } else {
                    user.setTitleId(TitleCode.NONE.getId());
                }
            }
            // 일반 칭호로 설정한 경우
            else {
                user.setTitleId(TitleCode.NONE.getId());
            }
        }

        // 대표 칭호 고유번호 변경
        if (request.getMainTitleId() != null) {
            // 보유 여부 확인
            if (request.getMainTitleId() == -1 ||
                    titleService.alreadyGetTitle(userId, request.getMainTitleId())) {
                user.setTitleId(request.getMainTitleId());
            } else {
                throw new NotOwnTitleException();
            }
        }

        // 캐릭터 종류 변경
        if (request.getCharacterType() != null) {
            user.setCharacterId(CharacterType.fromOrdinary(request.getCharacterType()));
        }
    }

    /* 보유 칭호목록 조회 */
    public TitleListResponse getTitleList(Long userId) {
        User user = getUserOrElseThrow(userId);

        return TitleListResponse.builder()
                .titleType(user.getTitleShowType().ordinal())
                .mainTitleId(user.getTitleId())
                .titles(titleService.getTitleList(userId))
                .build();
    }

    /* 유저가 지배하는 역 중 가장 비싼 역 반환 */
    private Station getMostExpensiveStation(Long userId) {
        List<DominatorLog> dominatorLogList = dominatorLogRepository.findByUserIdAndDominateStartTime(
                userId, systemData.getNowBaseTimeString());

        Station mostExpensiveStation = null;

        // 가장 비싼 역 찾기
        for (DominatorLog log : dominatorLogList) {
            Station nowStation = log.getStation();

            if (mostExpensiveStation == null
                    || mostExpensiveStation.getBalance() < nowStation.getBalance()) {
                mostExpensiveStation = nowStation;
            }
        }

        return mostExpensiveStation;
    }

    /* 코인 내역 조회 */
    public CoinLogResponse getCoinLog(Long userId, Pageable pageable) {
        User user = getUserOrElseThrow(userId);

        List<CoinInfoResponse> coinLogList = new ArrayList<>();

        // 요청받은 개수 & 페이지 데이터 읽어오기
        Page<TransactionLog> transactionLogPage = transactionLogRepository.findByUserOrderByCreatedAtDesc(
                user, pageable);

        for (TransactionLog log : transactionLogPage.getContent()) {
            coinLogList.add(CoinInfoResponse.builder()
                    .station(StationDto.of(log.getStation()))
                    .type(log.getType().getName())
                    .value(log.getValue())
                    .applied((log.getAppliedAt() != null))
                    .time(log.getCreatedAt()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .build());
        }

        return CoinLogResponse.builder()
                .list(coinLogList)
                .size(transactionLogPage.getPageable().getPageSize())
                .totalPage(transactionLogPage.getTotalPages())
                .currentPage(transactionLogPage.getPageable().getPageNumber())
                .build();
    }

    private User getUserOrElseThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private Station getStationOrElseThrow(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);
    }

    public Title getTitleOrElseThrow(Long titleId) {
        return titleRepository.findById(titleId)
                .orElseThrow(TitleNotFoundException::new);
    }
}
