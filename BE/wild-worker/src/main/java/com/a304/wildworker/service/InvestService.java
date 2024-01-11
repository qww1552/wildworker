package com.a304.wildworker.service;

import com.a304.wildworker.common.WebSocketUtils;
import com.a304.wildworker.domain.activestation.ActiveStation;
import com.a304.wildworker.domain.activestation.ActiveStationRepository;
import com.a304.wildworker.domain.activeuser.ActiveUserRepository;
import com.a304.wildworker.domain.common.TitleCode;
import com.a304.wildworker.domain.common.TitleShowType;
import com.a304.wildworker.domain.common.TransactionType;
import com.a304.wildworker.domain.dominator.DominatorLog;
import com.a304.wildworker.domain.dominator.DominatorLogRepository;
import com.a304.wildworker.domain.station.Station;
import com.a304.wildworker.domain.station.StationRepository;
import com.a304.wildworker.domain.system.SystemData;
import com.a304.wildworker.domain.user.User;
import com.a304.wildworker.domain.user.UserRepository;
import com.a304.wildworker.dto.response.*;
import com.a304.wildworker.dto.response.common.StationType;
import com.a304.wildworker.dto.response.common.TitleType;
import com.a304.wildworker.dto.response.common.WSBaseResponse;
import com.a304.wildworker.ethereum.contract.Bank;
import com.a304.wildworker.event.ChangedBalanceEvent;
import com.a304.wildworker.exception.StationNotFoundException;
import com.a304.wildworker.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.CipherException;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
@Service
public class InvestService {

    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final DominatorLogRepository dominatorLogRepository;
    private final ActiveStationRepository activeStationRepository;
    private final ActiveUserRepository activeUserRepository;
    private final Bank bank;
    private final SystemData systemData;

    private final ApplicationEventPublisher publisher;
    private final SimpMessagingTemplate messagingTemplate;

    private Map<Long, Station> mainDominateStationMap;

    /* 실시간 역 순위 */
    public StationRankResponse showStationRank(int size, String order) throws IOException {
        List<StationRankInfoResponse> rankingList = new ArrayList<>(size);
        List<Station> stationList = stationRepository.findByIdGreaterThan(0L);

        // 총 투자금액순 정렬
        if (order.equals("investment")) {
            Collections.sort(stationList,
                    Comparator.comparing(Station::getBalance, Comparator.reverseOrder()));
        }
        // 누적 수수료순 정렬
        else if (order.equals("commission")) {
            Collections.sort(stationList,
                    Comparator.comparing(Station::getCommission, Comparator.reverseOrder()));
        }

        for (int rank = 1; rank <= size; rank++) {
            Station station = stationList.get(rank - 1);
            StationInvestmentDto stationInvestmentDto = StationInvestmentDto.builder()
                    .id(station.getId())
                    .name(station.getName())
                    .totalInvestment(station.getBalance())
                    .prevCommission(station.getPrevCommission())
                    .currentCommission(station.getCommission())
                    .build();

            StationRankInfoResponse stationRankInfo = StationRankInfoResponse.builder()
                    .rank(rank)
                    .station(stationInvestmentDto)
                    .build();

            rankingList.add(stationRankInfo);
        }

        return StationRankResponse.builder()
                .ranking(rankingList)
                .orderBy(order)
                .build();
    }

    /* 해당 역에 대한 지분 조회 */
    public InvestmentInfoResponse showInvestmentByStation(Long stationId, Long userId)
            throws IOException {
        Station station = getStationOrElseThrow(stationId);
        ActiveStation activeStation = activeStationRepository.findById(station.getId());
        Map<Long, Long> investors = activeStation.getInvestors();
        final int RANK_LIMIT = 5;

        // 랭킹 정보
        List<InvestmentRankResponse> rankList = new ArrayList<>(RANK_LIMIT);
        InvestmentRankResponse mine = null;
        List<Entry<Long, Long>> investInfoList = new ArrayList<>(investors.entrySet());
        investInfoList.sort(Map.Entry.<Long, Long>comparingByValue().reversed());

        // 해당 역의 지배자 정보 조회
        Optional<DominatorLog> dominatorLog = dominatorLogRepository.findByStationIdAndDominateStartTime(
                stationId, systemData.getNowBaseTimeString());

        // 해당 역에 지배자가 있는 경우
        String dominatorName = null;
        Long dominatorId = -1L;
        if (dominatorLog.isPresent()) {
            dominatorName = dominatorLog.get().getUser().getName();
            dominatorId = dominatorLog.get().getUser().getId();
        }

        // 랭킹 세팅
        int rank = 1;
        Entry<Long, Long> dominatorInvestInfo = null;
        for (Map.Entry<Long, Long> entry : investInfoList) {
            InvestmentRankResponse rankResponse = null;

            // 지배자 정보 저장
            if (dominatorId.equals(entry.getKey())) {
                dominatorInvestInfo = entry;
            }

            // 5위까지 세팅
            if (rank <= 5) {
                rankResponse = getInvestmentRankResponse(entry, station);
                rankList.add(rankResponse);
            }

            // 내 지분 정보
            if (entry.getKey().equals(userId)) {
                if (rankResponse == null) {
                    rankResponse = getInvestmentRankResponse(entry, station);
                    rankResponse.setRank(rank);
                }

                mine = rankResponse;
            }

            rank++;
        }

        // 1위 금액 투자자 중 지배자가 있다면 랭킹 1위로 올림
        setDominatorFirstIfExistInRankList(dominatorId, dominatorInvestInfo, rankList, investInfoList, station);

        // 랭킹 번호 매기기
        for (int i = 0; i < rankList.size(); i++) {
            rankList.get(i).setRank(i + 1);
        }

        InvestmentInfoResponse response = InvestmentInfoResponse.builder()
                .stationName(station.getName())
                .dominator(dominatorName)
                .totalInvestment(station.getBalance())
                .prevCommission(station.getPrevCommission())
                .currentCommission(station.getCommission())
                .ranking(rankList)
                .mine(mine)
                .build();

        return response;
    }

    public InvestmentRankResponse getInvestmentRankResponse(Map.Entry<Long, Long> entry,
                                                            Station station) {
        User user = getUserOrElseThrow(entry.getKey());
        return InvestmentRankResponse.builder()
                .name(user.getName())
                .investment(entry.getValue())
                .percent(getPercent(station.getBalance(), entry.getValue()))
                .build();
    }

    public void setDominatorFirstIfExistInRankList(Long dominatorId, Entry<Long, Long> dominatorInvestInfo, List<InvestmentRankResponse> rankList, List<Entry<Long, Long>> investInfoList, Station station) {
        if (isInInvestInfoList(dominatorInvestInfo)) {
            return;
        }

        if (isNotSameDominatorAmountAndRankFirstAmount(dominatorInvestInfo, rankList)) {
            return;
        }

        int index = getIndexOfDominator(dominatorId, investInfoList);
        rankList.remove(index);
        rankList.add(0, getInvestmentRankResponse(dominatorInvestInfo, station));
    }

    private boolean isNotSameDominatorAmountAndRankFirstAmount(Entry<Long, Long> dominatorInvestInfo,
                                                               List<InvestmentRankResponse> rankList) {
        Long dominatorAmount = dominatorInvestInfo.getValue();
        if (!dominatorAmount.equals(rankList.get(0).getInvestment())) {
            return true;
        }
        return false;
    }

    private boolean isInInvestInfoList(Entry<Long, Long> dominatorInvestInfo) {
        if (dominatorInvestInfo == null) {
            return true;
        }
        return false;
    }

    private int getIndexOfDominator(Long dominatorId, List<Entry<Long, Long>> investInfoList) {
        int index = 0;
        for (Entry<Long, Long> investInfo : investInfoList) {
            if (investInfo.getKey().equals(dominatorId)) {
                return index;
            }
            index++;
        }

        return -1;
    }

    /* 내가 투자한 역 목록 조회 */
    public MyInvestmentResponse showMyInvestment(Long userId, String orderBy, String ascend)
            throws IOException {
        User user = getUserOrElseThrow(userId);
        List<MyInvestmentInfoResponse> investmentList = new LinkedList<>();

        // 내 투자 리스트
        for (long id = 1L, stationCnt = stationRepository.count(); id <= stationCnt; id++) {
            ActiveStation activeStation = activeStationRepository.findById(id);
            Map<Long, Long> investors = activeStation.getInvestors();

            Long amount = investors.get(user.getId());
            if (amount != null) {
                Station station = getStationOrElseThrow(id);
                investmentList.add(MyInvestmentInfoResponse.builder()
                        .station(StationDto.of(station))
                        .investment(amount)
                        .percent(getPercent(station.getBalance(), amount))
                        .build());
            }
        }

        // 이름순 정렬
        if (orderBy.equals("name")) {
            if (ascend.equals("ASC")) {
                Collections.sort(investmentList,
                        Comparator.comparing(i -> i.getStation().getName()));
            } else {
                Collections.sort(investmentList, Comparator.comparing(i -> i.getStation().getName(),
                        Comparator.reverseOrder()));
            }
        }
        // 투자금액순 정렬
        else if (orderBy.equals("investment")) {
            if (ascend.equals("ASC")) {
                Collections.sort(investmentList,
                        Comparator.comparing(MyInvestmentInfoResponse::getInvestment));
            } else {
                Collections.sort(investmentList,
                        Comparator.comparing(MyInvestmentInfoResponse::getInvestment,
                                Comparator.reverseOrder()));
            }
        }
        // 지분율순 정렬
        else if (orderBy.equals("percent")) {
            if (ascend.equals("ASC")) {
                Collections.sort(investmentList,
                        Comparator.comparing(MyInvestmentInfoResponse::getPercent));
            } else {
                Collections.sort(investmentList,
                        Comparator.comparing(MyInvestmentInfoResponse::getPercent,
                                Comparator.reverseOrder()));
            }
        }

        // 수수료 정산까지 남은 시간
        int remainSec = (int) Duration.between(LocalDateTime.now(), systemData.getNextBaseTime())
                .getSeconds();

        MyInvestmentResponse response = MyInvestmentResponse.builder()
                .investments(investmentList)
                .remainSec(remainSec)
                .orderBy(orderBy)
                .ascend(ascend)
                .build();

        return response;
    }

    public int getPercent(Long allValue, Long myValue) {
        if (allValue.equals(0L)) {
            return 0;
        }
        return (int) ((((myValue * 100) * 1000) / allValue) / 1000);
    }

    /* 역 투자 */
    @Transactional
    public void investToStation(Long stationId, Long userId, Long amount)
            throws CipherException, IOException {
        User user = getUserOrElseThrow(userId);
        Station station = getStationOrElseThrow(stationId);
        ActiveStation activeStation = activeStationRepository.findById(stationId);

        user.invest(amount);
        station.invest(amount);
        activeStation.invest(user.getId(), amount);

        // 코인 변동 이벤트 발생
        publisher.publishEvent(
                new ChangedBalanceEvent(user, station, TransactionType.INVESTMENT, amount * -1));
    }

    /**
     * 10분에 한 번 실행되는 메소드. 모든 역에 대해 투자 지분에 맞게 수수료를 분배한다.
     *
     * @throws CipherException
     * @throws IOException
     */
    @Scheduled(cron = "0 0/10 * * * *")
    @Transactional
    public void distributeInvestReward() throws IOException {
        // 10분 타임라인 시간 갱신
        systemData.updateBaseTime();

        // 투자금 초기화 시간 갱신
        LocalDateTime nowDateTime = systemData.getNowBaseTime();
        DayOfWeek dayOfWeek = nowDateTime.getDayOfWeek();
        boolean isInitInvestmentTime = false;

        if (dayOfWeek == DayOfWeek.MONDAY &&
                (nowDateTime.getHour() == 0 && nowDateTime.getMinute() == 0)) {
            systemData.initInvestmentTime();
            isInitInvestmentTime = true;
        }

        // 지배자의 대표 역 map 초기화
        mainDominateStationMap = new ConcurrentHashMap<>();

        for (Station station : stationRepository.findAll()) {
            ActiveStation activeStation = activeStationRepository.findById(station.getId());

            // 수수료 정산(DB) 후 새 지배자 얻기
            User dominator = distributeCommissionAndGetDominator(station, activeStation);

            // 누적 수수료 초기화
            station.resetCommission();

            //매 주 월요일 0시에는 누적금도 전부 초기화
            if (isInitInvestmentTime) {
                station.resetBalance();
                activeStation.resetInvestors();
            }

            // 지배자 설정
            setNewDominatorAndTitle(station, dominator);

            // 이더리움에서도 정산 요청
            bank.distributeInvestReward(station).thenAccept(
                    (receipt -> log.info("distribute invest reward receipt : {}", receipt)));
        }

        // 지배자 칭호 갱신
        updateDominatorTitle();
    }

    /**
     * 이 메소드는 실제 블록체인 네트워크에 기록된 내용이 아니므로 정확하지 않을 수 있음(소수점 처리 과정이 정확하지 않을 수 있음)
     */
    /* station의 수수료 정산 후 새 지배자를 반환 */
    @Transactional
    public User distributeCommissionAndGetDominator(Station station, ActiveStation activeStation) {
        Map<Long, Long> investors = activeStation.getInvestors();
        User dominator = null;
        Long maxInvestment = 0L;

        for (Entry<Long, Long> entry : investors.entrySet()) {
            User user = getUserOrElseThrow(entry.getKey());
            Long investment = entry.getValue();

            // 지분율 1위 찾기
            if (maxInvestment <= investment) {
                maxInvestment = investment;
                dominator = user;
            }

            // 지분율에 따라 수수료 정산
            long userShare = getPercent(station.getBalance(), investment);
            long money = station.getCommission() * userShare / 100;
            user.changeBalance(money);

            // 코인 변동 이벤트 발생
            if (money > 0) {
                publisher.publishEvent(
                        new ChangedBalanceEvent(user, station, TransactionType.INVESTMENT_REWARD,
                                money));
            }
        }

        return dominator;
    }

    /* 지배자 설정 */
    @Transactional
    public void setNewDominatorAndTitle(Station station, User dominator) {
        String dominatorName = null;

        // 현재 지배자가 존재하는 경우
        if (dominator != null) {
            // 지배자 설정
            dominatorName = dominator.getName();
            dominatorLogRepository.save(
                    new DominatorLog(station, dominator, systemData.getNowBaseTimeString()));

            // 해당 유저가 <지배자> 칭호 장착 중일 때
            if (dominator.getTitleShowType() == TitleShowType.DOMINATOR) {
                // 해당 유저가 지배한 역 중 가장 비싼 역 찾아서 갱신
                Station mostExpensiveStation = mainDominateStationMap.get(dominator.getId());
                if (mostExpensiveStation == null ||
                        mostExpensiveStation.getBalance() < station.getBalance()) {
                    mainDominateStationMap.put(dominator.getId(), station);
                }
            }

            // 이전 지배자 찾기
            Optional<DominatorLog> prevDominatorLog =
                    dominatorLogRepository.findByStationIdAndDominateStartTime(station.getId(),
                            systemData.getPrevBaseTimeString());

            // 이전 지배자 존재하는 경우
            if (prevDominatorLog.isPresent()) {
                User prevDominator = prevDominatorLog.get().getUser();

                // 이전 지배자가 지배자 칭호 전시 중 && 지배자 바뀐 경우
                if ((prevDominator.getTitleShowType() == TitleShowType.DOMINATOR)
                        && (prevDominator.getId() != dominator.getId())) {

                    // 이전 지배자 정보 변경
                    prevDominator.setTitleId(TitleCode.NONE.getId());

                    // 대표 칭호 변동 Noti 송신
                    sendMainTitleUpdateNotification(prevDominator.getId(),
                            new TitleDto(TitleCode.NONE.getId(), "x"));
                }
            }
        }

        // 새로운 지배자를 역 구독자들에게 Noti (지배자가 변경되지 않은 경우에도 보냄)
        WSBaseResponse<String> response = WSBaseResponse.station(StationType.CHANGE_DOMINATOR)
                .data(dominatorName);

        messagingTemplate.convertAndSend("/sub/stations/" + station.getId(), response);
    }

    /* 지배자 칭호 갱신 */
    @Transactional
    public void updateDominatorTitle() {
        for (Map.Entry<Long, Station> entry : mainDominateStationMap.entrySet()) {
            User user = getUserOrElseThrow(entry.getKey());
            Station newStation = entry.getValue();

            // 기존 칭호와 달라진 경우
            if (!user.getTitleId().equals(newStation.getId())) {
                // 대표 칭호 역 설정
                user.setTitleId(newStation.getId());

                // 대표 칭호 변동 Noti 송신
                sendMainTitleUpdateNotification(user.getId(), TitleDto.of(newStation));
            }
        }
    }

    /* 대표 칭호 변동 Noti 송신 */
    public void sendMainTitleUpdateNotification(Long userId, TitleDto title) {
        activeUserRepository.findById(userId).ifPresent(activeUser -> {
            WSBaseResponse<TitleDto> response = WSBaseResponse.title(TitleType.MAIN_TITLE_UPDATE)
                    .data(title);

            messagingTemplate.convertAndSendToUser(activeUser.getWebsocketSessionId(),
                    "/queue", response,
                    WebSocketUtils.createHeaders(activeUser.getWebsocketSessionId()));
        });
    }

    private User getUserOrElseThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private Station getStationOrElseThrow(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(StationNotFoundException::new);
    }
}
