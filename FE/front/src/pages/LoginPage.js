import * as React from "react";
import "./LoginPage.css";
import loginTitle from "../asset/image/intro_title.png";
import loginKakao from "../asset/image/kakao_login.png";

function LoginPage() {
  function loginClickHandler() {
    // 로그인 버튼 클릭시 이벤트
    console.log("kakao login btn clicked!");
  }
  return (
    <div className="login-background">
      <img className="login-title-img" src={loginTitle} alt="loginTitle" />
      <img
        className="login-kakao-img"
        onClick={loginClickHandler}
        src={loginKakao}
        alt="loginKakao"
      />
    </div>
  );
}
export default LoginPage;