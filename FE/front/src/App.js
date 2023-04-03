import React from "react";
import { useEffect, useState } from "react";
import Stomp from "stompjs";
import SockJS from "sockjs-client";
import { Routes, Route } from "react-router-dom";
import LoginPage from "./pages/LoginPage";
import RedirectLogin from "./pages/RedirectLoginPage";
import MainPage from "./pages/MainPage";
import SubwayMapPage from "./pages/SubwayMapPage";
import PvpPage from "./pages/PvpPage";
import PvpResultPage from "./pages/ResultPage";
import PvpReceipPage from "./pages/ReceiptPage";
import MySubwayPage from "./pages/MySubwayPage";
import HotSubwayPage from "./pages/HotSubwayPage";
import DetailSubwayPage from "./pages/DetailSubwayPage";
import MiniGamePage from "./pages/MiniGamePage";
import MiniGameReadyPage from "./pages/MiniGameReadyPage";
import Modal from "./components/mainpage/Modal";

import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import "./App.css";
import {
  connectSocket,
  subscribeStation,
  unsubscribeStation,
} from "../src/api/socketFunc";

function App() {
  // 웹에서 개발할 때, 얘 꼭 주석처리 해라

  // const elem = document.documentElement;
  // document.addEventListener('click', function() {
  //   if (elem.requestFullscreen) {
  //     elem.requestFullscreen();
  //   }
  // });

  const [isLogin, setIsLogin] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [isChangeId, setIsChangeId] = useState(false);

  // 유저 데이터
  const [userData, setUserData] = useState({
    characterType: 0,
    coin: 0,
    collectedPapers: 0,
    name: "이름바꿔",
    title: { id: 1, name: "x" },
    titleType: 0,
  });

  // 보유 칭호목록 조회 데이터
  const [myTitles, setMyTitles] = useState({
    titleType: 1,
    mainTitleId: 0,
    dominatorTitles: [{ id: 1, name: "역삼역의 지배자" }],
    titles: [{ id: 1, name: "없음" }],
  });

  // 코인 내역 조회 데이터
  const [myCoinLogs, setMyCoinLogs] = useState({
    balance: 1234,
    list: [
      {
        station: {
          id: 1,
          name: "역삼역",
        },
        type: "게임",
        value: -20,
        applied: true,
        time: "2023-03-14 14:20",
      },
    ],
    size: 10,
    totalPage: 10,
    currentPage: 1,
  });

  // 실시간 역 랭킹 데이터
  const [stationRank, setStationRank] = useState({
    ranking: [
      {
        rank: 1,
        station: {
          id: 1,
          name: "역삼역",
          totalInvestment: 12345,
          prevCommission: 1234,
          currentCommission: 123,
        },
      },
    ],
    orderBy: "investment",
  });

  // 해당 역에 대한 지분 데이터
  const [stationStake, setStationStake] = useState({
    stationName: null,
    dominator: null,
    totalInvestment: 0,
    prevCommission: 0,
    currentCommission: 0,
    ranking: [
      {
        rank: null,
        name: null,
        investment: 0,
        percent: null,
      },
    ],
    mine: {
      rank: null,
      investment: 0,
      percent: null,
    },
  });

  // 내가 투자한 역 목록
  const [myInvestList, setMyInvestList] = useState({
    investments: [
      {
        station: {
          id: 1,
          name: "역삼역",
        },
        investment: 1234,
        percent: 10,
      },
    ],
    remainSec: 90,
    orderBy: "investment",
    ascend: "ASC",
  });

  // 소켓 메세지로 넘어오는 데이터
  const [store, setStore] = useState({
    locationData: {
      prev: null,
      current: null,
      next: null,
    },
    manualMining: 1,
    dominatorAppear: "",
    dominatorMsg: "",
    coinChange: {
      AUTO_MINING: {},
      MANUAL_MINING: {},
      MINI_GAME_COST: {},
      MINI_GAME_RUN_COST: {},
      MINI_GAME_REWARD: {},
      INVESTMENT: {},
      INVESTMENT_REWARD: {},
    },
    getTitle: "",
    changeTitle: {},
    matching: {},
    gameStart: {},
    gameCancel: {},
    gameResult: {},
  });
  // 소켓 인스턴스 생성하고, 상태관리에 넣음
  const [stompClient, setStompClient] = useState({});

  // 연결하고, 필요한거 다 구독하고 상태관리에 넣어 유지함
  useEffect(() => {
    if (isLogin) {
      const socket = new SockJS("https://j8a304.p.ssafy.io/api/v1/ws");
      setStompClient(
        connectSocket(Stomp.over(socket), setStore, setUserData, store)
      );
      setIsConnected(true);
    }
  }, [isLogin]);

  // // 5초 뒤에 isChangeId = true로 지하철 Id가 변경되는 타이밍이라고 가정
  // const [isChangeId, setIsChangeId] = useState(false);
  // setTimeout(() => {
  //   setIsChangeId(true);
  // }, 5000);

  // isChangeId값의 변화로 지하철역 구독해제하고 새로운 지하철로 재연결
  useEffect(() => {
    if (store.locationData.prev) {
      setStompClient(unsubscribeStation(stompClient, store.locationData.prev));
      setStompClient(
        subscribeStation(stompClient, setStore, store.locationData.current)
      );
    }
  }, [store.locationData.prev]);

  // 실시간 위치 전송 코드
  useEffect(() => {
    if (isConnected) {
      const intervalId = setInterval(() => {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            if (position.coords) {
              handleSendLocation({
                lat: position.coords.latitude,
                lon: position.coords.longitude,
              });
            }
          },
          (error) => {
            console.log(error);
          }
        );
      }, 2000);
      return () => {
        clearInterval(intervalId);
      };
    }
  }, [isConnected]);

  // 위치 전송 백에게 전달하는 함수
  const handleSendLocation = (e) => {
    const message = JSON.stringify(e);
    stompClient.send("/pub/system/location", {}, message);
  };

  return (
    <div id="App" className="App">
      <Container className="app-container" maxWidth="xs">
        <Box sx={{ height: "100vh" }}>
          <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route
              path="/main"
              element={
                <MainPage
                  store={store}
                  setStore={setStore}
                  userData={userData}
                  setUserData={setUserData}
                  stompClient={stompClient}
                  setIsLogin={setIsLogin}
                  myTitles={myTitles}
                  setMytitles={setMyTitles}
                  myCoinLogs={myCoinLogs}
                  setMyCoinLogs={setMyCoinLogs}
                />
              }
            />
            <Route
              path="/redirect/login"
              element={
                <RedirectLogin
                  setIsLogin={setIsLogin}
                  setUserData={setUserData}
                />
              }
            />
            <Route path="/map" element={<SubwayMapPage />} />
            <Route
              path="/map/mine"
              element={
                <MySubwayPage
                  myInvestList={myInvestList}
                  setMyInvestList={setMyInvestList}
                />
              }
            />
            <Route path="/map/hot" element={<HotSubwayPage 
              stationRank={stationRank}
              setStationRank={setStationRank}
              stationStake={stationStake}
              setStationStake={setStationStake}
              setUserData={setUserData}
            />} />
            <Route
              path="/map/detail"
              element={
                <DetailSubwayPage
                  stationStake={stationStake}
                  setStationStake={setStationStake}
                  setUserData={setUserData}
                />
              }
            />
            <Route
              path="/pvp"
              element={
                <PvpPage
                  matchingData={store.matching}
                  gameRunData={store.gameCancel}
                  currentLocationData={store.locationData.current}
                  stompClient={stompClient}
                />
              }
            />
            <Route
              path="/pvp/ready"
              element={<MiniGameReadyPage gameStartData={store.gameStart} />}
            />
            <Route
              path="/pvp/minigame"
              element={<MiniGamePage stompClient={stompClient} />}
            />
            <Route
              path="/pvp/result"
              element={<PvpResultPage gameResultData={store.gameResult} />}
            />
            <Route
              path="/pvp/receipt"
              element={<PvpReceipPage gameResultData={store.gameResult} />}
            />
          </Routes>
        </Box>
      </Container>
    </div>
  );
}
export default App;
