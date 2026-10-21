import { useLocation, useNavigate } from 'react-router-dom';
import { MY_ROUTES } from '../../routes/paths';

/** 앱 안에서 들어왔으면 이전 화면, 링크로 바로 들어왔으면 `fallback`(DTL-01 뒤로가기와 같은 규칙). MobileAppBar와 MY-05 브레드크럼·취소가 같이 쓴다. */
export function useGoBack(fallback: string = MY_ROUTES.home): () => void {
  const navigate = useNavigate();
  const location = useLocation();
  return () => (location.key === 'default' ? navigate(fallback) : navigate(-1));
}
