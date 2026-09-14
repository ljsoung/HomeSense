import { MainLayout } from '../../components/layout/MainLayout';
import { HeroSection } from './HeroSection';
import { InterestRegionSummary } from './InterestRegionSummary';
import { MapBanner } from './MapBanner';
import { RecentViews } from './RecentViews';
import { RecommendedComplexes } from './RecommendedComplexes';
import { useFavoriteToggle } from './useFavoriteToggle';

/** SCR-HOME-01. */
export function HomePage() {
  const { favoritedIds, toggleFavorite } = useFavoriteToggle();

  return (
    <MainLayout>
      <HeroSection />
      <MapBanner />
      <section className="mx-auto grid max-w-[1280px] gap-5 px-4 pt-8 md:grid-cols-2 md:px-8">
        <InterestRegionSummary />
        <RecentViews favoritedIds={favoritedIds} onToggleFavorite={toggleFavorite} />
      </section>
      <RecommendedComplexes favoritedIds={favoritedIds} onToggleFavorite={toggleFavorite} />
    </MainLayout>
  );
}
