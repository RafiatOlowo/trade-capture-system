import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});


export const fetchTrades = () => api.get('/trades');

export const fetchAllUsers = async () => {
  console.log("Fetching all users from the API");
  return await api.get('/users').then((res) => {return res});
};

export const createUser = (user) => api.post('/users', user);

export const fetchUserProfiles = () => api.get('/userProfiles');

export const updateUser = (id, user) => api.put(`/users/${id}`, user);

export const authenticate = (user: string, pass: string) => {
  // New instance to temporarily override the Content-Type header
  const loginApi = axios.create({
    baseURL: 'http://localhost:8080', // Base URL must point to root to hit /login
    withCredentials: true,
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });

  // Spring Security looks for 'username' and 'password' fields by default
  const loginData = `username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}`;

  // Return the Promise from the POST call directly.
  return loginApi.post('/api/login', loginData);
}

/**
 * NEW LOGOUT FUNCTION:
 * - Sends a POST request to the Spring Security logout endpoint /api/logout.
 * - This request invalidates the server session and deletes the client cookie.
 */
export const logout = () => {
    return api.post('/logout'); 
}

export const getUserByLogin = (login: string) => {
    return api.get(`/users/loginId/${login}`);
}
export default api;

