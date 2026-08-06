// 1. 로그인 API 호출
export const loginApi = async (credentials) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(credentials),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || '로그인 오류가 발생했습니다.');
  }

  return await response.json();
};

// 2. 회원가입 API 호출
export const signupApi = async (userData) => {
  // userData: { username: '', password: '', name: '', email: '' 등 }
  const response = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(userData),
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.message || '회원가입 오류가 발생했습니다.');
  }

  return await response.json();
};