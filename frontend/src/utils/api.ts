import axios, { AxiosRequestConfig } from 'axios';

// Helper function to encode username:password into Base64 for HTTP Basic Auth
const encodeCredentials = (username: string, password: string): string => {
  // Use btoa for browser-side Base64 encoding
  return btoa(`${username}:${password}`);
};

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});


export const fetchTrades = () => api.get('/trades');

export const fetchAllUsers = async () => {
  console.log("Fetching all users from the API");
  return await api.get('/users').then((res) => {return res});
};

export const createUser = (user: any) => api.post('/users', user);

export const fetchUserProfiles = () => api.get('/userProfiles');

export const updateUser = (id: number, user: any) => api.put(`/users/${id}`, user);

// Switched from query params to HTTP Basic Authorization header
export const authenticate = (user: string, pass: string) => {
  const encodedAuth = encodeCredentials(user, pass);

  const config: AxiosRequestConfig = {
    headers: {
      'Authorization': `Basic ${encodedAuth}` // Standard format: Basic <Base64(user:pass)>
    }
  };

  return api.get('/users', config);
}

export const getUserByLogin = (login: string) => {
    return api.get(`/users/loginId/${login}`);
}
export default api;

