import { ToastProvider } from './components/ui/ToastProvider';
import { AuthProvider } from './features/auth/AuthProvider';
import { AppRouter } from './routes/AppRouter';

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <AppRouter />
      </ToastProvider>
    </AuthProvider>
  );
}

export default App;
