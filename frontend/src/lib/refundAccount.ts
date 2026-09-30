import type { OrderData } from '../api/types'

// 토스 은행 코드 (서버 BankCodes와 같은 목록) - 환불 계좌 폼의 은행 선택지
export const BANKS: { code: string; name: string }[] = [
  { code: '06', name: 'KB국민은행' }, { code: '88', name: '신한은행' }, { code: '20', name: '우리은행' },
  { code: '81', name: '하나은행' }, { code: '11', name: 'NH농협은행' }, { code: '03', name: 'IBK기업은행' },
  { code: '90', name: '카카오뱅크' }, { code: '92', name: '토스뱅크' }, { code: '89', name: '케이뱅크' },
  { code: '71', name: '우체국' }, { code: '45', name: '새마을금고' }, { code: '48', name: '신협' },
  { code: '07', name: 'Sh수협은행' }, { code: '12', name: '단위농협' }, { code: '02', name: 'KDB산업은행' },
  { code: '23', name: 'SC제일은행' }, { code: '27', name: '씨티은행' }, { code: '54', name: 'HSBC은행' },
  { code: '31', name: 'iM뱅크(대구)' }, { code: '32', name: '부산은행' }, { code: '34', name: '광주은행' },
  { code: '35', name: '제주은행' }, { code: '37', name: '전북은행' }, { code: '39', name: '경남은행' },
  { code: '50', name: '저축은행' }, { code: '64', name: '산림조합' },
]

/**
 * 입금까지 끝난 가상계좌 주문인지 - 카드와 달리 원래 결제수단으로 돌려줄 수 없어서 환불 계좌가 필요하다
 * (입금 전 취소는 돌려줄 돈이 없으므로 필요 없음. 판단 기준은 서버 OrderService.needsRefundAccount와 같음)
 */
export function needsRefundAccount(order: OrderData | undefined | null): boolean {
  return !!order && !!order.virtualAccountNumber && order.status !== 'AWAITING_DEPOSIT'
}
