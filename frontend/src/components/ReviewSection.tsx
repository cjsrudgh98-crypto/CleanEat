import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { ReviewItem, ReviewList } from '../api/types'

const MAX_CONTENT = 500
const RATING_WORDS = ['', '별로예요', '그저 그래요', '괜찮아요', '좋아요', '최고예요']

function errorMessage(err: unknown, fallback: string) {
  return err instanceof ApiError ? err.message : fallback
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

/** 읽기용 별 (4.3 -> ★★★★☆) */
function Stars({ rating, size = '0.95rem' }: { rating: number; size?: string }) {
  const rounded = Math.round(rating)
  return (
    <span className="stars" style={{ fontSize: size }} aria-label={`별점 5점 중 ${rating}점`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= rounded ? 'star on' : 'star'} aria-hidden>
          ★
        </span>
      ))}
    </span>
  )
}

/** 별점 고르기 - 라디오 그룹이라 화살표 키로도 고를 수 있다 */
function StarInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  const [hover, setHover] = useState(0)
  const shown = hover || value
  return (
    <div className="star-input" role="radiogroup" aria-label="별점" onPointerLeave={() => setHover(0)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <label key={n} className={n <= shown ? 'on' : ''} onPointerEnter={() => setHover(n)}>
          <input
            type="radio"
            name="rating"
            value={n}
            checked={value === n}
            onChange={() => onChange(n)}
            aria-label={`${n}점`}
          />
          <span aria-hidden>★</span>
        </label>
      ))}
      <span className="star-input-word">{shown ? RATING_WORDS[shown] : '별점을 골라주세요'}</span>
    </div>
  )
}

function ReviewForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: ReviewItem
  submitLabel: string
  onSubmit: (rating: number, content: string) => Promise<void>
  onCancel?: () => void
}) {
  const [rating, setRating] = useState(initial?.rating ?? 0)
  const [content, setContent] = useState(initial?.content ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (rating === 0) {
      setError('별점을 골라주세요.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSubmit(rating, content.trim())
    } catch (err) {
      setError(errorMessage(err, '저장하지 못했습니다.'))
      setSaving(false)
    }
  }

  return (
    <form className="review-form" onSubmit={submit}>
      <StarInput value={rating} onChange={setRating} />
      <label htmlFor="reviewContent" className="sr-only">
        리뷰 내용
      </label>
      <textarea
        id="reviewContent"
        rows={3}
        maxLength={MAX_CONTENT}
        placeholder="맛, 성분, 배송은 어땠나요? (별점만 남겨도 돼요)"
        value={content}
        onChange={(e) => setContent(e.target.value)}
      />
      <div className="review-form-foot">
        <span className="muted-text">
          {content.length}/{MAX_CONTENT}
        </span>
        <div style={{ display: 'flex', gap: '0.4rem' }}>
          {onCancel && (
            <button type="button" className="btn" onClick={onCancel}>
              취소
            </button>
          )}
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? '저장 중...' : submitLabel}
          </button>
        </div>
      </div>
      {error && <div className="error-box" style={{ marginTop: '0.5rem' }}>{error}</div>}
    </form>
  )
}

function ReviewRow({ review, onEdit, onDelete }: { review: ReviewItem; onEdit?: () => void; onDelete?: () => void }) {
  return (
    <li className="review-row">
      <div className="review-row-head">
        <Stars rating={review.rating} size="0.85rem" />
        <strong>{review.nickname}</strong>
        {review.mine && <span className="tag" style={{ fontSize: '0.68rem', padding: '0.05rem 0.45rem' }}>내 리뷰</span>}
        <span className="muted-text">
          {formatDate(review.createdAt)}
          {review.updatedAt && ' (수정됨)'}
        </span>
      </div>
      {review.content && <p className="review-content">{review.content}</p>}
      {(onEdit || onDelete) && (
        <div style={{ display: 'flex', gap: '0.6rem' }}>
          {onEdit && (
            <button type="button" className="link-btn" onClick={onEdit}>
              수정
            </button>
          )}
          {onDelete && (
            <button type="button" className="link-btn" style={{ color: 'var(--high-fg)' }} onClick={onDelete}>
              삭제
            </button>
          )}
        </div>
      )}
    </li>
  )
}

/** 상품 상세 아래 리뷰·별점 영역 */
export function ReviewSection({
  productId,
  onSummaryChange,
}: {
  productId: number
  onSummaryChange?: (averageRating: number | null, reviewCount: number) => void
}) {
  const { auth } = useAuth()
  const [data, setData] = useState<ReviewList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  const load = useCallback(async () => {
    try {
      const result = await api.getReviews(productId, auth)
      setData(result)
      setError(null)
      onSummaryChange?.(result.averageRating, result.reviewCount)
    } catch (err) {
      setError(errorMessage(err, '리뷰를 불러오지 못했습니다.'))
    }
  }, [productId, auth, onSummaryChange])

  useEffect(() => {
    let cancelled = false
    api
      .getReviews(productId, auth)
      .then((result) => {
        if (cancelled) return
        setData(result)
        setError(null)
      })
      .catch((err) => !cancelled && setError(errorMessage(err, '리뷰를 불러오지 못했습니다.')))
    return () => {
      cancelled = true
    }
  }, [productId, auth])

  async function create(rating: number, content: string) {
    if (!auth) return
    await api.createReview(productId, rating, content, auth)
    await load()
  }

  async function update(rating: number, content: string) {
    if (!auth || !data?.myReview) return
    await api.updateReview(data.myReview.id, rating, content, auth)
    setEditing(false)
    await load()
  }

  async function remove() {
    if (!auth || !data?.myReview || !window.confirm('리뷰를 삭제할까요?')) return
    try {
      await api.deleteReview(data.myReview.id, auth)
      await load()
    } catch (err) {
      setError(errorMessage(err, '삭제하지 못했습니다.'))
    }
  }

  if (error && !data) return <div className="card"><div className="error-box">{error}</div></div>
  if (!data) return <div className="card muted-text">리뷰를 불러오는 중...</div>

  const others = data.reviews.filter((r) => !r.mine)
  const maxCount = Math.max(...data.ratingCounts, 1)

  return (
    <section className="card review-section" aria-labelledby="reviewTitle">
      <h2 id="reviewTitle" className="section-title">
        리뷰 {data.reviewCount > 0 && <span className="muted-text">{data.reviewCount.toLocaleString()}</span>}
      </h2>

      {data.reviewCount > 0 && data.averageRating !== null && (
        <div className="review-summary">
          <div className="review-average">
            <strong>{data.averageRating.toFixed(1)}</strong>
            <Stars rating={data.averageRating} />
          </div>
          {/* 별점 분포 - 한 가지 값(리뷰 수)이라 한 색 막대, 개수는 막대 옆 글자로 */}
          <ol className="rating-dist" aria-label="별점 분포">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = data.ratingCounts[star - 1]
              return (
                <li key={star}>
                  <span className="rating-dist-label">{star}점</span>
                  <span className="rating-dist-track" aria-hidden>
                    <span className="rating-dist-fill" style={{ width: `${(count / maxCount) * 100}%` }} />
                  </span>
                  <span className="rating-dist-count">{count}</span>
                </li>
              )
            })}
          </ol>
        </div>
      )}

      {data.canWrite && (
        <div className="review-write">
          <p style={{ margin: '0 0 0.5rem', fontWeight: 700, fontSize: '0.9rem' }}>구매하신 상품은 어떠셨나요?</p>
          <ReviewForm submitLabel="리뷰 등록" onSubmit={create} />
        </div>
      )}
      {data.writeBlockedReason === 'NOT_PURCHASED' && (
        <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>이 상품을 구매한 회원만 리뷰를 쓸 수 있어요.</p>
      )}
      {data.writeBlockedReason === 'LOGIN_REQUIRED' && (
        <p className="muted-text" style={{ margin: '0 0 0.75rem' }}>로그인하고 구매한 상품에 리뷰를 남겨보세요.</p>
      )}

      {data.myReview &&
        (editing ? (
          <div className="review-write">
            <p style={{ margin: '0 0 0.5rem', fontWeight: 700, fontSize: '0.9rem' }}>내 리뷰 수정</p>
            <ReviewForm initial={data.myReview} submitLabel="수정 저장" onSubmit={update} onCancel={() => setEditing(false)} />
          </div>
        ) : (
          <ul className="review-list mine">
            <ReviewRow review={data.myReview} onEdit={() => setEditing(true)} onDelete={remove} />
          </ul>
        ))}
      {error && <div className="error-box" style={{ marginBottom: '0.75rem' }}>{error}</div>}

      {data.reviewCount === 0 ? (
        <p style={{ margin: 0, fontSize: '0.88rem' }}>아직 리뷰가 없어요.{data.canWrite && ' 첫 리뷰를 남겨주세요!'}</p>
      ) : (
        <ul className="review-list">
          {others.map((r) => (
            <ReviewRow key={r.id} review={r} />
          ))}
        </ul>
      )}
    </section>
  )
}
