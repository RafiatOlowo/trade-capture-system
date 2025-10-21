import { observer } from 'mobx-react-lite';
import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import Admin from "./pages/Admin";
import Main from './pages/Main';
import MiddleOffice from "./pages/MiddleOffice";
import SignIn from './pages/SignIn';
import Support from "./pages/Support";
import TraderSales from "./pages/TraderSales";
import PrivateRoute from "./PrivateRoute";
import userStore from './stores/userStore';

const AppRouter = observer(() => {
    // Restore user data from localStorage when mounted
    useEffect(() => {
        const storedUser = localStorage.getItem('user');
        const storedProfile = localStorage.getItem('userProfile');
        
        if (storedUser && storedProfile) {
            try {
                const user = JSON.parse(storedUser);

                userStore.user = user;
                userStore.authorization = storedProfile;
            } catch (error) {
                console.error("Error restoring user from localStorage:", error);
                localStorage.removeItem('user');
                localStorage.removeItem('userProfile');
            }
        }
    }, []);

    return (
        <BrowserRouter>
            <Routes>
                <Route path="/signin" element={<SignIn/>}/>
                <Route path="/home" element={<PrivateRoute><Main/></PrivateRoute>}/>
                <Route path="/trade" element={<PrivateRoute><TraderSales/></PrivateRoute>}/>
                <Route path="/middle-office" element={<PrivateRoute><MiddleOffice/></PrivateRoute>}/>
                <Route path="/support" element={<PrivateRoute><Support/></PrivateRoute>}/>
                <Route path="/admin" element={<PrivateRoute><Admin/></PrivateRoute>}/>
                <Route
                    path="*"
                    element={
                        userStore.user ? <Navigate to="/home" replace/> : <Navigate to="/signin" replace/>
                    }
                />
            </Routes>
        </BrowserRouter>
    );
});

export default AppRouter;