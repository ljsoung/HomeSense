// e2e 전용 가짜 카카오맵 JS SDK. map01-check가 dapi.kakao.com/v2/maps/sdk.js 요청을 이 파일로 바꿔 준다 — 실제 SDK는
// 도메인 등록·네트워크에 좌우돼 결정적인 테스트가 어렵다. MAP-01이 쓰는 범위(features/map/kakaoTypes.ts)만 흉내 낸다:
// 지도 중심·레벨·bounds, CustomOverlay를 지도 안 DOM에 절대 위치로 그리기, idle·click 이벤트, 클러스터러(묶지 않고 모두
// 그린다), Geocoder·Places(FAKE_GEOCODE 표에 있는 이름만 찾음). 화면 좌표 계산은 앱의 approximateBounds와 같은 축척
// (레벨 3에서 1px ≈ 0.9m)을 쓴다.
(function () {
  const FAKE_GEOCODE = {
    '경기도 수원시 장안구': { y: '37.3040', x: '127.0100' },
    '서울특별시 강남구': { y: '37.5172', x: '127.0473' },
  };

  function LatLng(lat, lng) {
    this.lat = lat;
    this.lng = lng;
  }
  LatLng.prototype.getLat = function () {
    return this.lat;
  };
  LatLng.prototype.getLng = function () {
    return this.lng;
  };

  function LatLngBounds(sw, ne) {
    this.sw = sw;
    this.ne = ne;
  }
  LatLngBounds.prototype.getSouthWest = function () {
    return this.sw;
  };
  LatLngBounds.prototype.getNorthEast = function () {
    return this.ne;
  };
  LatLngBounds.prototype.extend = function () {};

  const listeners = new Map();
  const event = {
    addListener(target, type, handler) {
      const key = target;
      if (!listeners.has(key)) listeners.set(key, []);
      listeners.get(key).push({ type, handler });
    },
    removeListener(target, type, handler) {
      const list = listeners.get(target) || [];
      listeners.set(
        target,
        list.filter((l) => !(l.type === type && l.handler === handler)),
      );
    },
    trigger(target, type, ...args) {
      (listeners.get(target) || []).filter((l) => l.type === type).forEach((l) => l.handler(...args));
    },
  };

  function degPerPx(level, lat) {
    const metersPerPx = 0.9 * Math.pow(2, level - 3);
    return { lat: metersPerPx / 111320, lng: metersPerPx / (111320 * Math.cos((lat * Math.PI) / 180)) };
  }

  function KMap(container, options) {
    this.container = container;
    this.center = options.center;
    this.level = options.level;
    this.overlays = new Set();
    this.pane = document.createElement('div');
    this.pane.dataset.fakeMap = 'pane';
    this.pane.style.cssText = 'position:absolute;inset:0;overflow:hidden;background:#eef3f8;';
    container.appendChild(this.pane);
    this.pane.addEventListener('click', (e) => {
      if (e.target === this.pane) event.trigger(this, 'click');
    });
    window.__fakeMap = this;
    this._idleSoon();
  }
  KMap.prototype._idleSoon = function () {
    clearTimeout(this._idleTimer);
    this._idleTimer = setTimeout(() => {
      this._render();
      event.trigger(this, 'idle');
    }, 30);
  };
  KMap.prototype._size = function () {
    return { w: this.container.clientWidth || 800, h: this.container.clientHeight || 600 };
  };
  KMap.prototype._render = function () {
    const { w, h } = this._size();
    const d = degPerPx(this.level, this.center.lat);
    this.overlays.forEach((o) => {
      const x = (o.position.lng - this.center.lng) / d.lng + w / 2;
      const y = (this.center.lat - o.position.lat) / d.lat + h / 2;
      o.wrapper.style.left = `${x}px`;
      o.wrapper.style.top = `${y}px`;
    });
  };
  KMap.prototype.getCenter = function () {
    return this.center;
  };
  KMap.prototype.setCenter = function (latlng) {
    this.center = latlng;
    this._idleSoon();
  };
  KMap.prototype.panTo = function (latlng) {
    this.setCenter(latlng);
  };
  KMap.prototype.getLevel = function () {
    return this.level;
  };
  KMap.prototype.setLevel = function (level) {
    this.level = level;
    this._idleSoon();
  };
  KMap.prototype.getBounds = function () {
    const { w, h } = this._size();
    const d = degPerPx(this.level, this.center.lat);
    return new LatLngBounds(
      new LatLng(this.center.lat - (h / 2) * d.lat, this.center.lng - (w / 2) * d.lng),
      new LatLng(this.center.lat + (h / 2) * d.lat, this.center.lng + (w / 2) * d.lng),
    );
  };
  KMap.prototype.setBounds = function (bounds) {
    this.center = new LatLng((bounds.sw.lat + bounds.ne.lat) / 2, (bounds.sw.lng + bounds.ne.lng) / 2);
    this._idleSoon();
  };
  KMap.prototype.relayout = function () {
    this._render();
  };

  function CustomOverlay(options) {
    this.position = options.position;
    this.content = options.content;
    this.zIndex = options.zIndex || 0;
    this.wrapper = document.createElement('div');
    this.wrapper.style.position = 'absolute';
    this.wrapper.style.transform = `translate(${-(options.xAnchor ?? 0.5) * 100}%, ${-(options.yAnchor ?? 0.5) * 100}%)`;
    this.wrapper.style.zIndex = String(this.zIndex);
    this.wrapper.appendChild(this.content);
    this.map = null;
    if (options.map) this.setMap(options.map);
  }
  CustomOverlay.prototype.setMap = function (map) {
    if (this.map) {
      this.map.overlays.delete(this);
      this.wrapper.remove();
    }
    this.map = map;
    if (map) {
      map.overlays.add(this);
      map.pane.appendChild(this.wrapper);
      map._render();
    }
  };
  CustomOverlay.prototype.setPosition = function (latlng) {
    this.position = latlng;
    if (this.map) this.map._render();
  };
  CustomOverlay.prototype.getPosition = function () {
    return this.position;
  };
  CustomOverlay.prototype.setZIndex = function (z) {
    this.zIndex = z;
    this.wrapper.style.zIndex = String(z);
  };

  function MarkerClusterer(options) {
    this.map = options.map;
    this.markers = new Set();
    window.__fakeClusterer = this;
  }
  MarkerClusterer.prototype.addMarkers = function (markers) {
    markers.forEach((m) => {
      this.markers.add(m);
      m.setMap(this.map);
    });
  };
  MarkerClusterer.prototype.removeMarkers = function (markers) {
    markers.forEach((m) => {
      this.markers.delete(m);
      m.setMap(null);
    });
  };
  MarkerClusterer.prototype.clear = function () {
    this.removeMarkers([...this.markers]);
  };
  MarkerClusterer.prototype.redraw = function () {};

  const Status = { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' };
  function lookup(query, cb) {
    setTimeout(() => {
      const hit = FAKE_GEOCODE[query];
      if (hit) cb([hit], Status.OK);
      else cb([], Status.ZERO_RESULT);
    }, 10);
  }
  function Geocoder() {}
  Geocoder.prototype.addressSearch = lookup;
  function Places() {}
  Places.prototype.keywordSearch = lookup;

  window.kakao = {
    maps: {
      load(cb) {
        setTimeout(cb, 0);
      },
      LatLng,
      LatLngBounds,
      Map: KMap,
      CustomOverlay,
      MarkerClusterer,
      event,
      services: { Geocoder, Places, Status },
    },
  };
})();
