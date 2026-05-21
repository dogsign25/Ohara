import { useEffect, useRef } from 'react'

// 간단한 인터랙티브 캔버스 배경 (노드-엣지 그래프 시뮬레이션)
function GraphCanvas() {
  const ref = useRef()

  useEffect(() => {
    const canvas = ref.current
    const ctx = canvas.getContext('2d')
    let raf

    const resize = () => {
      canvas.width  = window.innerWidth
      canvas.height = window.innerHeight
    }
    resize()
    window.addEventListener('resize', resize)

    // 노드 생성
    const nodes = Array.from({ length: 38 }, (_, i) => ({
      x:  Math.random() * canvas.width,
      y:  Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 0.35,
      vy: (Math.random() - 0.5) * 0.35,
      r:  Math.random() * 2.5 + 1.5,
      color: ['#60a5fa', '#fbbf24', '#c084fc'][i % 3],
    }))

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      // 엣지 (거리 기반)
      for (let i = 0; i < nodes.length; i++) {
        for (let j = i + 1; j < nodes.length; j++) {
          const dx = nodes[i].x - nodes[j].x
          const dy = nodes[i].y - nodes[j].y
          const d  = Math.sqrt(dx * dx + dy * dy)
          if (d < 160) {
            ctx.beginPath()
            ctx.moveTo(nodes[i].x, nodes[i].y)
            ctx.lineTo(nodes[j].x, nodes[j].y)
            ctx.strokeStyle = `rgba(255,255,255,${0.06 * (1 - d / 160)})`
            ctx.lineWidth = 0.8
            ctx.stroke()
          }
        }
      }

      // 노드
      nodes.forEach(n => {
        ctx.beginPath()
        ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2)
        ctx.fillStyle = n.color + '99'
        ctx.fill()
      })

      // 물리 이동
      nodes.forEach(n => {
        n.x += n.vx
        n.y += n.vy
        if (n.x < 0 || n.x > canvas.width)  n.vx *= -1
        if (n.y < 0 || n.y > canvas.height) n.vy *= -1
      })

      raf = requestAnimationFrame(draw)
    }
    draw()

    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', resize)
    }
  }, [])

  return (
    <canvas
      ref={ref}
      className="absolute inset-0 w-full h-full"
      style={{ opacity: 0.55 }}
    />
  )
}

// 통계 카드
function Stat({ value, label }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <span className="text-2xl font-bold text-white tracking-tight" style={{ fontFamily: "'Courier New', monospace" }}>
        {value}
      </span>
      <span className="text-white/35 text-xs uppercase tracking-widest">{label}</span>
    </div>
  )
}

// 기능 카드
function Feature({ icon, title, desc }) {
  return (
    <div className="border border-white/8 rounded-2xl p-5 bg-white/3 hover:bg-white/6 hover:border-white/15 transition-all duration-300">
      <div className="text-2xl mb-3">{icon}</div>
      <div className="text-white/90 font-medium text-sm mb-1.5">{title}</div>
      <div className="text-white/40 text-xs leading-relaxed">{desc}</div>
    </div>
  )
}

export default function Landing({ onLogin, onRegister }) {
  return (
    <div className="min-h-screen bg-[#050810] relative overflow-hidden flex flex-col">
      {/* 배경 그래프 애니메이션 */}
      <GraphCanvas />

      {/* 배경 그라디언트 오버레이 */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: 'radial-gradient(ellipse 60% 50% at 50% 40%, rgba(96,165,250,0.06) 0%, transparent 70%)',
        }}
      />

      {/* 네비게이션 */}
      <nav className="relative z-10 flex items-center justify-between px-8 py-5">
        <div className="flex items-center gap-2">
          {/* 로고 마크 */}
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <circle cx="11" cy="11" r="3" fill="#60a5fa"/>
            <circle cx="4"  cy="4"  r="2" fill="#c084fc" opacity="0.8"/>
            <circle cx="18" cy="5"  r="2" fill="#fbbf24" opacity="0.8"/>
            <circle cx="4"  cy="18" r="2" fill="#fbbf24" opacity="0.8"/>
            <circle cx="18" cy="17" r="2" fill="#c084fc" opacity="0.8"/>
            <line x1="11" y1="11" x2="4"  y2="4"  stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="18" y2="5"  stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="4"  y2="18" stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
            <line x1="11" y1="11" x2="18" y2="17" stroke="rgba(255,255,255,0.2)" strokeWidth="0.8"/>
          </svg>
          <span className="text-white font-bold tracking-widest text-sm uppercase">OHARA</span>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={onLogin}
            className="text-white/50 hover:text-white text-sm transition-colors px-4 py-2"
          >
            로그인
          </button>
          <button
            onClick={onRegister}
            className="bg-white/10 hover:bg-white/15 border border-white/15 hover:border-white/25 text-white text-sm px-4 py-2 rounded-xl transition-all duration-200"
          >
            시작하기
          </button>
        </div>
      </nav>

      {/* 히어로 */}
      <main className="relative z-10 flex-1 flex flex-col items-center justify-center px-8 text-center">
        {/* 배지 */}
        <div className="inline-flex items-center gap-2 border border-blue-400/20 bg-blue-400/5 rounded-full px-4 py-1.5 mb-8">
          <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
          <span className="text-blue-300/80 text-xs tracking-widest uppercase">실시간 글로벌 인텔리전스</span>
        </div>

        {/* 헤드라인 */}
        <h1
          className="text-white mb-5 leading-none"
          style={{
            fontFamily: "'Courier New', monospace",
            fontSize: 'clamp(2.8rem, 7vw, 5.5rem)',
            fontWeight: 900,
            letterSpacing: '-0.02em',
          }}
        >
          세계는<br />
          <span style={{ color: '#60a5fa' }}>연결</span>되어 있다
        </h1>

        <p className="text-white/40 mb-10 max-w-md leading-relaxed" style={{ fontSize: '0.95rem' }}>
          뉴스에서 자동 추출한 국가·기관·인물의 관계를
          실시간 인터랙티브 그래프로 시각화합니다.
        </p>

        {/* CTA */}
        <div className="flex items-center gap-3 mb-16">
          <button
            onClick={onRegister}
            className="flex items-center gap-2 bg-blue-500 hover:bg-blue-400 text-white font-semibold px-6 py-3 rounded-xl transition-all duration-200 text-sm"
          >
            무료로 시작하기
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 8l4 4m0 0l-4 4m4-4H3"/>
            </svg>
          </button>
          <button
            onClick={onLogin}
            className="text-white/50 hover:text-white/80 text-sm px-4 py-3 transition-colors"
          >
            이미 계정이 있어요 →
          </button>
        </div>

        {/* 통계 */}
        <div className="flex items-center gap-10 mb-6">
          <Stat value="8+" label="뉴스 소스" />
          <div className="w-px h-8 bg-white/10" />
          <Stat value="5분" label="업데이트 주기" />
          <div className="w-px h-8 bg-white/10" />
          <Stat value="3종" label="엔티티 유형" />
        </div>
      </main>

      {/* 기능 소개 */}
      <section className="relative z-10 px-8 pb-16 max-w-3xl mx-auto w-full">
        <div className="grid grid-cols-3 gap-3">
          <Feature
            icon="🌐"
            title="실시간 수집"
            desc="BBC, Al Jazeera, Guardian 등 8개 소스에서 세계정세 뉴스를 5분마다 자동 수집합니다."
          />
          <Feature
            icon="🕸️"
            title="관계 그래프"
            desc="spaCy NER로 추출한 국가·기관·인물을 d3 포스 그래프로 시각화합니다."
          />
          <Feature
            icon="🔍"
            title="심층 분석"
            desc="노드를 클릭하면 연결된 엔티티와 원문 기사를 즉시 확인할 수 있습니다."
          />
        </div>
      </section>

      {/* 노드 범례 */}
      <footer className="relative z-10 flex items-center justify-center gap-6 pb-8 text-xs text-white/25">
        {[['#60a5fa', '국가'], ['#fbbf24', '기관·조직'], ['#c084fc', '인물']].map(([c, l]) => (
          <div key={l} className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full" style={{ backgroundColor: c }} />
            {l}
          </div>
        ))}
      </footer>
    </div>
  )
}