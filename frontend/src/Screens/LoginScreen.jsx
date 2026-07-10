import { useNavigate } from "react-router-dom";
import "./LoginScreen.css";

function LoginScreen() {
  const navigate = useNavigate();

  const handleSignup = () => {
    navigate("/signup");
  };
  const handleMain = () => {
    navigate("/Main");
  };

  return (
    <div >
        <h1>로그인</h1>

        <button onClick={handleSignup}>
            회원가입
        </button>
        <button onClick={handleMain}>
            홈
        </button>
    </div>
  );
}

export default LoginScreen;