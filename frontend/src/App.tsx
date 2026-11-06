import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import RequireAuth from './components/RequireAuth'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import { AcceptInvite, ResetPassword, VerifyEmail } from './pages/TokenPages'
import { DoctorDetail, DoctorList } from './pages/Doctors'
import Book from './pages/Book'
import MyAppointments from './pages/MyAppointments'
import VisitSummary from './pages/VisitSummary'
import { NotFound, StaffPlaceholder } from './pages/Misc'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />
        <Route path="verify-email" element={<VerifyEmail />} />
        <Route path="reset-password" element={<ResetPassword />} />
        <Route path="accept-invite" element={<AcceptInvite />} />
        <Route path="doctors" element={<DoctorList />} />
        <Route path="doctors/:id" element={<DoctorDetail />} />
        <Route path="book" element={<Book />} />
        <Route path="me/appointments" element={<RequireAuth roles={['PATIENT']}><MyAppointments /></RequireAuth>} />
        <Route path="me/visits/:id" element={<RequireAuth roles={['PATIENT']}><VisitSummary /></RequireAuth>} />
        <Route path="reception/*" element={<RequireAuth roles={['RECEPTIONIST', 'ADMIN', 'SUPER_ADMIN']}><StaffPlaceholder title="Front desk" /></RequireAuth>} />
        <Route path="doctor/*" element={<RequireAuth roles={['DOCTOR']}><StaffPlaceholder title="Doctor's day" /></RequireAuth>} />
        <Route path="admin/*" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><StaffPlaceholder title="Clinic admin" /></RequireAuth>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
