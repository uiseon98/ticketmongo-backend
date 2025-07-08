import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// 환경 설정
const concertId = 111;
const baseUrl = 'http://localhost:8080/api';
const wsUrl = 'ws://localhost:8080/ws/waitqueue';

export const options = {
    scenarios: {
        realistic_user_journey: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            maxDuration: '10m', // 더 긴 시간으로 설정
        },
    },
};

// 실제 사용자가 검색할 만한 키워드들
const searchKeywords = [
    '아이유', 'IU', '콘서트', '2025',
    'BTS', '방탄소년단', '블랙핑크',
    '아티스트', '라이브', '공연'
];

// 실제 사용자 행동 시뮬레이션 함수들
function simulateSearchBehavior(authParams) {
    console.log(`[사용자 행동] 콘서트 검색 시작...`);

    // 1. 콘서트 목록 먼저 확인 (대부분 사용자가 하는 행동)
    const listRes = http.get(`${baseUrl}/concerts?page=0&size=20`, authParams);
    check(listRes, { '콘서트 목록 조회 성공': (r) => r.status === 200 });

    // 현실적인 사용자 행동: 1-3초 정도 목록을 훑어봄
    sleep(Math.random() * 2 + 1);

    // 2. 키워드 검색 (50% 확률로 검색)
    if (Math.random() > 0.5) {
        const keyword = randomItem(searchKeywords);
        console.log(`[사용자 행동] "${keyword}" 검색 중...`);

        const searchRes = http.get(`${baseUrl}/concerts/search?query=${encodeURIComponent(keyword)}`, authParams);
        check(searchRes, { '콘서트 검색 성공': (r) => r.status === 200 });

        // 검색 결과 확인하는 시간
        sleep(Math.random() * 3 + 2);
    }

    // 3. 필터링 시도 (30% 확률로 필터 사용)
    if (Math.random() > 0.7) {
        console.log(`[사용자 행동] 날짜 필터링 중...`);

        // 다음 달 공연 필터링 (가격 필터링은 제거됨)
        const today = new Date();
        const nextMonth = new Date(today.getFullYear(), today.getMonth() + 1, 1);
        const endDate = new Date(today.getFullYear(), today.getMonth() + 2, 0);

        const filterParams = new URLSearchParams({
            startDate: nextMonth.toISOString().split('T')[0],
            endDate: endDate.toISOString().split('T')[0]
        });

        const filterRes = http.get(`${baseUrl}/concerts/filter?${filterParams}`, authParams);
        check(filterRes, { '콘서트 날짜 필터링 성공': (r) => r.status === 200 });

        sleep(Math.random() * 2 + 1);
    }
}

function simulateDetailViewing(authParams) {
    console.log(`[사용자 행동] 콘서트 상세 페이지 확인...`);

    // 4. 특정 콘서트 상세 정보 조회
    const detailRes = http.get(`${baseUrl}/concerts/${concertId}`, authParams);
    check(detailRes, { '콘서트 상세 조회 성공': (r) => r.status === 200 });

    // 상세 정보 읽는 시간 (현실적으로 10-30초)
    const readingTime = Math.random() * 20 + 10;
    console.log(`[사용자 행동] 상세 정보를 ${readingTime.toFixed(1)}초 동안 읽는 중...`);
    sleep(readingTime);

    // 5. AI 요약 정보 확인 (호기심 많은 사용자)
    if (Math.random() > 0.3) {
        console.log(`[사용자 행동] AI 요약 정보 확인...`);
        const aiSummaryRes = http.get(`${baseUrl}/concerts/${concertId}/ai-summary`, authParams);
        check(aiSummaryRes, { 'AI 요약 조회 성공': (r) => r.status === 200 });
        sleep(Math.random() * 5 + 3);
    }

    // 6. 좌석 현황 미리 확인해보기 (일부 사용자의 행동)
    if (Math.random() > 0.6) {
        console.log(`[사용자 행동] 좌석 현황 미리 확인...`);
        // 헤더 없이 좌석 조회 시도 (대기열 없이는 안 될 거라는 걸 알지만 해보는 사용자들)
        const seatCheckRes = http.get(`${baseUrl}/seats/concerts/${concertId}/status`, authParams);
        // 실패할 것으로 예상 (대기열 통과 안 했으니까)
        console.log(`[사용자 행동] 좌석 미리보기 시도 결과: ${seatCheckRes.status}`);
        sleep(2);
    }
}

function simulateHesitation() {
    // 7. 고민하는 시간 (현실적인 사용자 행동!)
    const hesitationTime = Math.random() * 30 + 10; // 10-40초 고민
    console.log(`[사용자 심리] ${hesitationTime.toFixed(1)}초 동안 구매 고민 중... 💭`);
    console.log(`[사용자 심리] "진짜 살까? 돈이 너무 비싼데... 하지만 좋아하는 가수인데..."`);
    sleep(hesitationTime);

    // 일부 사용자는 포기할 수도 있음
    if (Math.random() > 0.85) {
        console.log(`[사용자 심리] "역시 너무 비싸다... 다음에 사자" (포기)`);
        return false; // 포기하고 대기열 진입 안 함
    }

    console.log(`[사용자 심리] "그래, 질러버리자! 인생은 한 번뿐이야!" (결심)`);
    return true; // 대기열 진입 결정
}

export default function () {
    const username = `K6TESTUSER${__VU}`;
    const password = "1q2w3e4r!";

    console.log(`\n🎭 [${username}] 콘서트 예매 여정 시작!`);

    // === 1단계: 로그인 ===
    console.log(`[${username}] 1단계: 로그인 시도...`);
    const loginPayload = `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
    const loginParams = { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } };
    const loginRes = http.post(`${baseUrl}/auth/login`, loginPayload, loginParams);

    check(loginRes, { '로그인 성공': r => r.status === 200 });
    if (loginRes.status !== 200) {
        console.error(`[${username}] ❌ 로그인 실패! 여정 종료.`);
        return;
    }

    const cookieHeader = `access=${loginRes.cookies.access[0].value}; refresh=${loginRes.cookies.refresh[0].value}`;
    const authParams = {
        headers: { 'Cookie': cookieHeader, 'Content-Type': 'application/json' },
    };

    console.log(`[${username}] ✅ 로그인 성공!`);

    // === 2단계: 현실적인 탐색 행동 ===
    console.log(`[${username}] 2단계: 콘서트 탐색 시작...`);
    simulateSearchBehavior(authParams);

    // === 3단계: 상세 정보 확인 ===
    console.log(`[${username}] 3단계: 관심 콘서트 상세 확인...`);
    simulateDetailViewing(authParams);

    // === 4단계: 구매 결정 고민 ===
    console.log(`[${username}] 4단계: 구매 결정 고민...`);
    const shouldProceed = simulateHesitation();

    if (!shouldProceed) {
        console.log(`[${username}] 🚪 예매 포기하고 사이트 이탈`);
        return;
    }

    // === 5단계: 대기열 진입 결정! ===
    console.log(`[${username}] 5단계: 드디어 대기열 진입 시도! 🎯`);

    // 마지막 순간 긴장감 (버튼 누르기 전 1-3초 망설임)
    const finalHesitation = Math.random() * 2 + 1;
    console.log(`[${username}] 💫 "예매하기" 버튼 앞에서 ${finalHesitation.toFixed(1)}초 망설임...`);
    sleep(finalHesitation);

    const queueEnterRes = http.post(`${baseUrl}/queue/enter?concertId=${concertId}`, null, authParams);
    const queueStatusData = queueEnterRes.json().data;
    let accessKey = null;

    check(queueEnterRes, { '대기열 진입 요청 성공': (r) => r.status === 200 });

    // === 6단계: 대기열 처리 ===
    if (queueStatusData.status === 'IMMEDIATE_ENTRY') {
        console.log(`[${username}] 🎉 와! 즉시 입장 성공! 럭키!`);
        accessKey = queueStatusData.accessKey;
    } else if (queueStatusData.status === 'WAITING') {
        console.log(`[${username}] ⏳ 대기열 입장. 현재 ${queueStatusData.rank}번째...`);
        console.log(`[${username}] 💭 "언제 들어갈 수 있을까... 다른 사람들은 얼마나 기다리지?"`);

        const url = `${wsUrl}?concertId=${concertId}`;
        const wsRes = ws.connect(url, authParams, function (socket) {
            socket.on('open', () => {
                console.log(`[${username}] 🔗 실시간 대기열 연결 성공!`);
                console.log(`[${username}] 💭 "오케이, 이제 기다리기만 하면 되는구나"`);
            });

            socket.on('message', function (data) {
                const msg = JSON.parse(data);

                if (msg.type === 'ADMIT' && msg.accessKey) {
                    console.log(`[${username}] 🎊 드디어 입장 허가! 예매 페이지로 GO!`);
                    accessKey = msg.accessKey;
                    socket.close();
                }

                if (msg.type === 'RANK_UPDATE') {
                    console.log(`[${username}] 📊 순위 업데이트: ${msg.rank}번째 (조금씩 앞으로!)`);
                    if (msg.rank <= 3) {
                        console.log(`[${username}] 💓 심장이 두근두근... 거의 다 왔다!`);
                    }
                }
            });

            socket.on('close', () => console.log(`[${username}] 🔌 대기열 연결 종료`));

            socket.setTimeout(function () {
                console.log(`[${username}] ⏰ 대기 시간 초과... 너무 오래 기다렸어요 😢`);
                socket.close();
            }, 180000);

            socket.on('error', function (e) {
                if (e.error() !== 'websocket: close sent') {
                    console.error(`[${username}] 🚨 연결 오류: ${e.error()}`);
                }
            });
        });

        check(wsRes, { 'WebSocket 연결 성공': (r) => r && r.status === 101 });
    }

    // === 7단계: 최종 예매 페이지 접근 ===
    if (accessKey) {
        console.log(`[${username}] 🎯 최종 단계: 예매 페이지 접근!`);

        const finalParams = {
            headers: {
                'Cookie': cookieHeader,
                'Content-Type': 'application/json',
                'X-Access-Key': accessKey,
            },
        };

        // 실제 예매 페이지에서 하는 행동들
        console.log(`[${username}] 🪑 좌석 현황 확인 중...`);
        const seatRes = http.get(`${baseUrl}/seats/concerts/${concertId}/status`, finalParams);
        check(seatRes, {
            '좌석 조회 성공 (예매 페이지 진입)': (r) => r.status === 200,
        });

        if (seatRes.status === 200) {
            console.log(`[${username}] ✨ 성공! 이제 좌석을 선택할 수 있어요!`);
            console.log(`[${username}] 🎭 여기서부터는 좌석 선택, 결제 과정이 이어집니다...`);
        }
    } else {
        console.log(`[${username}] 😭 아쉽게도 예매 기회를 놓쳤습니다... 다음에 다시 도전!`);
    }

    console.log(`[${username}] 🏁 콘서트 예매 여정 완료!\n`);
    sleep(1);
}