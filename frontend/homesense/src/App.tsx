import { AppErrorBoundary } from './components/ui/AppErrorBoundary';
import { ToastProvider } from './components/ui/ToastProvider';
import { AuthProvider } from './features/auth/AuthProvider';
import { AppRouter } from './routes/AppRouter';

function App() {
  // 바깥 바운더리는 Provider까지 감싸는 마지막 안전망이다. 화면 오류는 AppRouter 안의 바운더리가 잡고 라우트가 바뀌면 풀린다.
  return (
    <AppErrorBoundary>
      <AuthProvider>
        <ToastProvider>
          <AppRouter />
        </ToastProvider>
      </AuthProvider>
    </AppErrorBoundary>
  );
}

export default App;
