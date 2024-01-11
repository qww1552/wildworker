package com.a304.wildworker.domain.activestation;

import com.a304.wildworker.domain.common.TransactionType;
import com.a304.wildworker.domain.station.StationRepository;
import com.a304.wildworker.domain.system.SystemData;
import com.a304.wildworker.domain.transaction.TransactionLog;
import com.a304.wildworker.domain.transaction.TransactionLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ActiveStationRepository {

    private final ConcurrentHashMap<Long, ActiveStation> activeStations;

    private final SystemData systemData;
    private final TransactionLogRepository transactionLogRepository;
    private final StationRepository stationRepository;

    public ActiveStationRepository(SystemData systemData,
                                   TransactionLogRepository transactionLogRepository,
                                   StationRepository stationRepository) {
        activeStations = new ConcurrentHashMap<>();
        this.systemData = systemData;
        this.transactionLogRepository = transactionLogRepository;
        this.stationRepository = stationRepository;

        for (long id = 1L, stationCnt = stationRepository.count(); id <= stationCnt; id++) {
            save(new ActiveStation(id));
        }

        // 투자내역 세팅
        List<TransactionLog> transactionLogList = transactionLogRepository.findByTypeAndCreatedAtGreaterThanEqual(
                TransactionType.INVESTMENT, systemData.getInvestmentBaseTime());

        for (TransactionLog investmentLog : transactionLogList) {
            ActiveStation station = findById(investmentLog.getStation().getId());
            station.invest(investmentLog.getUser().getId(), investmentLog.getValue() * -1);
        }

    }

    public ActiveStation findById(Long id) {
        if (!activeStations.containsKey(id)) {
            save(new ActiveStation(id));
        }
        return activeStations.get(id);
    }

    private ActiveStation save(ActiveStation station) {
        activeStations.put(station.getId(), station);
        return station;
    }

}
