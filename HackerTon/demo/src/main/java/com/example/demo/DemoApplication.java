package com.example.demo;

// Spring Boot 실행 및 웹 계층에 필요한 라이브러리
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 세션 관리 (로그인 상태 유지)
import jakarta.servlet.http.HttpSession;

// 날짜/시간 처리 및 컬렉션 라이브러리
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap; // 여러 요청이 동시에 접근해도 안전한 Map
import java.util.stream.Collectors;

// ──────────────────────────────────────────────
// 애플리케이션 진입점
// Spring Boot를 시작하는 메인 클래스
// ──────────────────────────────────────────────
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

// ──────────────────────────────────────────────
// 수강신청 타임어택 게임 컨트롤러
// 게임 시작 및 과목 신청 요청을 처리한다.
// 기본 URL: /api/game
// ──────────────────────────────────────────────
@RestController
@RequestMapping("/api/game")
class GameController {

    // 게임 시작 시각 (시간 초과 판단 기준)
    private LocalDateTime startTime;

    // 제한 시간(초) 및 신청해야 할 과목 수
    private final int TIME_LIMIT_SECONDS = 30;
    private Set<String> mySubjects = new HashSet<>();
    private final int TOTAL_SUBJECTS_NEEDED = 5;

    // POST /api/game/start
    // 게임을 초기화하고 시작 시각을 기록한다.
    @PostMapping("/start")
    public String startGame() {
        startTime = LocalDateTime.now();
        mySubjects.clear();
        return "게임 시작! 제한시간: " + TIME_LIMIT_SECONDS + "초";
    }

    // POST /api/game/enroll
    // 과목 신청 요청을 처리한다.
    // 제한 시간 초과 시 FORBIDDEN(403)을 반환하고,
    // 5개 과목을 모두 신청하면 성공 메시지를 반환한다.
    @PostMapping("/enroll")
    public ResponseEntity<String> enrollSubject(@RequestBody String subjectName) {

        // 현재 시각이 제한 시간을 넘었는지 확인
        if (Duration.between(startTime, LocalDateTime.now()).getSeconds() > TIME_LIMIT_SECONDS) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("GAME OVER: 시간 초과!");
        }

        mySubjects.add(subjectName);

        // 목표 과목 수 달성 시 성공 처리
        if (mySubjects.size() >= TOTAL_SUBJECTS_NEEDED) {
            return ResponseEntity.ok("ALL CLEAR! 수강신청 성공!");
        }

        return ResponseEntity.ok(subjectName + " 신청 완료 (" + mySubjects.size() + "/" + TOTAL_SUBJECTS_NEEDED + ")");
    }
}

// ──────────────────────────────────────────────
// 수강신청 오픈 시간 판단 컨트롤러
// 서버 시각이 10:00:00 이후인지 확인하는 API를 제공한다.
// 프론트엔드에서 버튼 클릭 시 이 API를 호출하여
// 수강신청 가능 여부를 판단한다.
// ──────────────────────────────────────────────
@RestController
class TimeController {

    // 수강신청이 시작되는 기준 시각 (10:00:00)
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0, 0);

    // GET /api/time/check
    // 현재 서버 시각을 기준으로 수강신청 가능 여부를 반환한다.
    // 응답 예시: { "isOpen": false, "currentTime": "09:58:32", "openTime": "10:00:00" }
    @GetMapping("/api/time/check")
    public ResponseEntity<Map<String, Object>> checkEnrollmentTime() {
        LocalTime now = LocalTime.now();

        // OPEN_TIME 이전이면 isOpen = false, 이후면 isOpen = true
        boolean isOpen = !now.isBefore(OPEN_TIME);

        Map<String, Object> result = new HashMap<>();
        result.put("isOpen", isOpen);
        result.put("currentTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        result.put("openTime", OPEN_TIME.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return ResponseEntity.ok(result);
    }
}

// ──────────────────────────────────────────────
// 과목별 여석 타이머 컨트롤러
//
// 각 과목에는 수강신청이 열린 뒤 여석이 유지되는 제한 시간이 있다.
// 서버가 타이머 시작 시각을 기록하고, 조회 시점에 경과 시간을 계산하여
// 여석 여부(hasSeats)와 남은 시간(remainingSeconds)을 반환한다.
//
// 흐름:
//   1. POST /api/subject/timer/start  → 10:00:00 에 모든 과목 타이머 시작
//   2. GET  /api/subject/timer/check/{subjectName} → 버튼 클릭 시 여석 확인
//      - remainingSeconds > 0 : hasSeats=true  → 수강신청 매크로 창 표시
//      - remainingSeconds = 0 : hasSeats=false → "수강여석이 없습니다!" 창 표시
// ──────────────────────────────────────────────
@RestController
@RequestMapping("/api/subject")
class SubjectTimerController {

    // ── 과목별 여석 유지 시간 설정 ──────────────────
    // 각 과목마다 수강신청 오픈 후 여석이 남아있는 시간(초)을 정의한다.
    // 이 값을 변경하면 과목별 타이머 길이를 조정할 수 있다.
    private static final Map<String, Integer> SUBJECT_DURATIONS = new LinkedHashMap<>();

    static {
        SUBJECT_DURATIONS.put("알고리즘",   120); // 2분
        SUBJECT_DURATIONS.put("운영체제",    60); // 1분
        SUBJECT_DURATIONS.put("데이터베이스", 90); // 1분 30초
        SUBJECT_DURATIONS.put("컴퓨터구조",  45); // 45초
        SUBJECT_DURATIONS.put("네트워크",    75); // 1분 15초
    }

    // 과목별 타이머 시작 시각을 저장하는 맵
    // static으로 선언하여 EnrollmentController에서도 여석 여부를 확인할 수 있게 한다.
    // key: 과목명, value: 타이머가 시작된 서버 시각
    static final Map<String, LocalDateTime> startTimes = new ConcurrentHashMap<>();

    // POST /api/subject/timer/start
    // 수강신청 오픈(10:00:00)과 동시에 모든 과목의 타이머를 시작한다.
    // 이미 시작된 상태에서 재호출하면 타이머가 초기화된다.
    @PostMapping("/timer/start")
    public ResponseEntity<Map<String, Object>> startAllTimers() {
        LocalDateTime now = LocalDateTime.now();

        // 모든 과목의 시작 시각을 현재 시각으로 기록
        startTimes.clear();
        for (String subject : SUBJECT_DURATIONS.keySet()) {
            startTimes.put(subject, now);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "모든 과목 타이머 시작됨");
        result.put("startTime", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        result.put("subjects", SUBJECT_DURATIONS); // 과목별 제한 시간 정보 함께 반환

        return ResponseEntity.ok(result);
    }

    // GET /api/subject/timer/check/{subjectName}
    // 특정 과목의 타이머 잔여 시간을 계산하여 여석 여부를 반환한다.
    //
    // 응답 예시 (여석 있음): { "hasSeats": true,  "remainingSeconds": 47, "subjectName": "알고리즘" }
    // 응답 예시 (여석 없음): { "hasSeats": false, "remainingSeconds": 0,  "subjectName": "운영체제" }
    @GetMapping("/timer/check/{subjectName}")
    public ResponseEntity<Map<String, Object>> checkTimer(
            @PathVariable String subjectName) {

        // 등록되지 않은 과목명이면 404 반환
        if (!SUBJECT_DURATIONS.containsKey(subjectName)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "등록되지 않은 과목입니다: " + subjectName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("subjectName", subjectName);

        // 타이머가 아직 시작되지 않은 경우 (10:00 전 호출)
        if (!startTimes.containsKey(subjectName)) {
            result.put("hasSeats", false);
            result.put("remainingSeconds", 0);
            result.put("message", "타이머가 아직 시작되지 않았습니다.");
            return ResponseEntity.ok(result);
        }

        // 타이머 시작 이후 경과한 시간(초) 계산
        long elapsed = Duration.between(startTimes.get(subjectName), LocalDateTime.now()).getSeconds();

        // 과목의 제한 시간에서 경과 시간을 빼 잔여 시간 산출
        long remaining = SUBJECT_DURATIONS.get(subjectName) - elapsed;

        // 잔여 시간이 0보다 크면 여석 있음, 0 이하면 여석 없음
        boolean hasSeats = remaining > 0;

        result.put("hasSeats", hasSeats);
        result.put("remainingSeconds", Math.max(0, remaining)); // 음수 방지
        result.put("durationSeconds", SUBJECT_DURATIONS.get(subjectName));

        return ResponseEntity.ok(result);
    }

    // GET /api/subject/timer/list
    // 등록된 모든 과목과 각 과목의 현재 여석 상태를 한번에 반환한다.
    // 프론트엔드에서 전체 과목 목록을 초기 로딩할 때 활용한다.
    @GetMapping("/timer/list")
    public ResponseEntity<List<Map<String, Object>>> listAllTimers() {
        List<Map<String, Object>> list = new ArrayList<>();

        for (String subject : SUBJECT_DURATIONS.keySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("subjectName", subject);
            item.put("durationSeconds", SUBJECT_DURATIONS.get(subject));

            if (startTimes.containsKey(subject)) {
                // 타이머가 시작된 경우 잔여 시간 계산
                long elapsed = Duration.between(startTimes.get(subject), LocalDateTime.now()).getSeconds();
                long remaining = SUBJECT_DURATIONS.get(subject) - elapsed;
                item.put("hasSeats", remaining > 0);
                item.put("remainingSeconds", Math.max(0, remaining));
            } else {
                // 타이머가 시작되지 않은 경우
                item.put("hasSeats", false);
                item.put("remainingSeconds", 0);
            }

            list.add(item);
        }

        return ResponseEntity.ok(list);
    }
}

// ──────────────────────────────────────────────
// 수강신청 매크로 기믹 컨트롤러
//
// 과목 신청 버튼을 누르면 여러 기믹 중 하나가 무작위로 선택되어
// 해당 기믹 정보를 프론트엔드에 반환한다.
// 프론트엔드는 응답의 type 값에 따라 알맞은 기믹 UI를 렌더링한다.
//
// 기믹 종류 5가지:
//   CLICK_COUNT     - 버튼을 N번 빠르게 클릭
//   TYPE_CODE       - 랜덤 인증 코드 타이핑
//   MATH_QUIZ       - 간단한 수학 계산 문제
//   BUTTON_SEQUENCE - 버튼을 정해진 순서대로 클릭
//   SLIDER          - 슬라이더를 목표 위치로 이동
// ──────────────────────────────────────────────
@RestController
@RequestMapping("/api/gimmick")
class GimmickController {

    // 기믹 유형 식별자 상수
    private static final String CLICK_COUNT      = "CLICK_COUNT";
    private static final String TYPE_CODE        = "TYPE_CODE";
    private static final String MATH_QUIZ        = "MATH_QUIZ";
    private static final String BUTTON_SEQUENCE  = "BUTTON_SEQUENCE";
    private static final String SLIDER           = "SLIDER";

    // 무작위 선택에 사용할 기믹 유형 목록
    private static final List<String> GIMMICK_TYPES = List.of(
            CLICK_COUNT, TYPE_CODE, MATH_QUIZ, BUTTON_SEQUENCE, SLIDER
    );

    // 코드 생성에 사용할 문자 집합 (혼동하기 쉬운 0/O, 1/I/l 제외)
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    // 버튼 순서 기믹에 사용할 버튼 라벨 목록
    private static final List<String> SEQUENCE_LABELS = List.of("①", "②", "③", "④", "⑤", "⑥");

    private final Random random = new Random();

    // GET /api/gimmick/random
    // 기믹 유형 하나를 무작위로 골라 해당 기믹의 설정 값을 반환한다.
    // 프론트엔드는 응답의 type 필드를 읽어 UI를 결정한다.
    //
    // 공통 응답 필드:
    //   type        - 기믹 유형 문자열
    //   title       - 기믹 제목 (UI 헤더 표시용)
    //   description - 사용자에게 보여줄 안내 문구
    //   timeLimitSeconds - 제한 시간(초)
    //   data        - 기믹별 세부 설정 (아래 각 기믹 설명 참고)
    @GetMapping("/random")
    public ResponseEntity<Map<String, Object>> getRandomGimmick() {

        // 기믹 목록에서 무작위로 하나 선택
        String selectedType = GIMMICK_TYPES.get(random.nextInt(GIMMICK_TYPES.size()));

        Map<String, Object> response = buildGimmick(selectedType);
        return ResponseEntity.ok(response);
    }

    // GET /api/gimmick/random/{subjectName}
    // 특정 과목에 대한 기믹을 무작위로 반환한다.
    // 동시에 정답을 세션에 저장하여, 이후 수강신청 시도 시 서버에서 검증할 수 있게 한다.
    @GetMapping("/random/{subjectName}")
    public ResponseEntity<Map<String, Object>> getRandomGimmickForSubject(
            @PathVariable String subjectName,
            HttpSession session) {

        String selectedType = GIMMICK_TYPES.get(random.nextInt(GIMMICK_TYPES.size()));

        Map<String, Object> response = buildGimmick(selectedType);
        response.put("subjectName", subjectName);

        // 기믹 정답을 세션에 저장 (키: "gimmick_과목명")
        // EnrollmentController가 이 값을 꺼내 사용자 답변과 비교한다.
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Map<String, Object> gimmickSession = new HashMap<>();
        gimmickSession.put("type", selectedType);
        gimmickSession.put("correctAnswer", extractCorrectAnswer(selectedType, data));
        session.setAttribute("gimmick_" + subjectName, gimmickSession);

        return ResponseEntity.ok(response);
    }

    // 기믹 유형별 정답 값을 추출한다.
    // CLICK_COUNT·BUTTON_SEQUENCE는 클라이언트 완료 보고 방식이므로 "COMPLETED"로 처리한다.
    private Object extractCorrectAnswer(String type, Map<String, Object> data) {
        return switch (type) {
            case TYPE_CODE       -> data.get("code");
            case MATH_QUIZ       -> data.get("answer");
            case SLIDER          -> data.get("targetPercent");
            default              -> "COMPLETED"; // CLICK_COUNT, BUTTON_SEQUENCE
        };
    }

    // 선택된 유형에 맞는 기믹 데이터를 생성하여 Map으로 반환하는 내부 메서드
    private Map<String, Object> buildGimmick(String type) {
        Map<String, Object> gimmick = new HashMap<>();
        gimmick.put("type", type);

        switch (type) {

            // ── CLICK_COUNT ──────────────────────────────
            // 목표 횟수(targetCount)만큼 버튼을 빠르게 클릭해야 한다.
            // data: { targetCount: 5~20 사이 랜덤, timeLimitSeconds }
            case CLICK_COUNT -> {
                int target = 5 + random.nextInt(16); // 5~20
                gimmick.put("title", "빠르게 클릭!");
                gimmick.put("description", "버튼을 " + target + "번 클릭하세요!");
                gimmick.put("timeLimitSeconds", 10);
                gimmick.put("data", Map.of("targetCount", target));
            }

            // ── TYPE_CODE ────────────────────────────────
            // 화면에 표시된 랜덤 코드를 정확히 타이핑해야 한다.
            // data: { code: 6자리 랜덤 문자열 }
            case TYPE_CODE -> {
                String code = generateRandomCode(6);
                gimmick.put("title", "인증 코드 입력");
                gimmick.put("description", "아래 코드를 정확히 입력하세요.");
                gimmick.put("timeLimitSeconds", 15);
                gimmick.put("data", Map.of("code", code));
            }

            // ── MATH_QUIZ ────────────────────────────────
            // 간단한 사칙연산 문제를 풀어야 한다.
            // data: { expression: 계산식 문자열, answer: 정답 정수 }
            case MATH_QUIZ -> {
                Map<String, Object> quiz = generateMathQuiz();
                gimmick.put("title", "수학 문제");
                gimmick.put("description", "계산 결과를 입력하세요.");
                gimmick.put("timeLimitSeconds", 20);
                gimmick.put("data", quiz);
            }

            // ── BUTTON_SEQUENCE ──────────────────────────
            // 화면에 표시된 순서대로 버튼을 클릭해야 한다.
            // data: { sequence: 버튼 라벨 배열 (3~5개) }
            case BUTTON_SEQUENCE -> {
                List<String> seq = generateButtonSequence(3 + random.nextInt(3)); // 3~5개
                gimmick.put("title", "순서대로 클릭!");
                gimmick.put("description", "표시된 순서대로 버튼을 클릭하세요.");
                gimmick.put("timeLimitSeconds", 15);
                gimmick.put("data", Map.of("sequence", seq));
            }

            // ── SLIDER ───────────────────────────────────
            // 슬라이더를 목표 위치(%)에 맞춰 이동해야 한다.
            // 허용 오차(tolerancePercent) 이내면 성공으로 처리한다.
            // data: { targetPercent: 10~90, tolerancePercent: 5 }
            case SLIDER -> {
                int target = 10 + random.nextInt(81); // 10~90%
                gimmick.put("title", "슬라이더 조정");
                gimmick.put("description", "슬라이더를 " + target + "% 위치로 이동하세요.");
                gimmick.put("timeLimitSeconds", 10);
                gimmick.put("data", Map.of("targetPercent", target, "tolerancePercent", 5));
            }

            default -> {
                gimmick.put("title", "알 수 없는 기믹");
                gimmick.put("description", "");
                gimmick.put("timeLimitSeconds", 10);
                gimmick.put("data", Map.of());
            }
        }

        return gimmick;
    }

    // 지정된 길이의 랜덤 인증 코드를 생성한다.
    // 혼동하기 쉬운 문자(0/O, 1/l/I)는 CODE_CHARS에서 제외되어 있다.
    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    // 덧셈·뺄셈·곱셈 중 하나를 무작위로 골라 계산 문제와 정답을 생성한다.
    private Map<String, Object> generateMathQuiz() {
        int a = 1 + random.nextInt(50);   // 1~50
        int b = 1 + random.nextInt(50);   // 1~50
        int op = random.nextInt(3);        // 0=덧셈, 1=뺄셈, 2=곱셈

        String expression;
        int answer;

        switch (op) {
            case 0 -> { expression = a + " + " + b; answer = a + b; }
            case 1 -> { expression = a + " - " + b; answer = a - b; }
            default -> {
                // 곱셈은 숫자가 너무 커지지 않도록 범위를 제한
                a = 1 + random.nextInt(12);
                b = 1 + random.nextInt(12);
                expression = a + " × " + b;
                answer = a * b;
            }
        }

        Map<String, Object> quiz = new HashMap<>();
        quiz.put("expression", expression);
        quiz.put("answer", answer);
        return quiz;
    }

    // 주어진 개수만큼 버튼 라벨을 무작위 순서로 섞어 시퀀스를 생성한다.
    private List<String> generateButtonSequence(int count) {
        List<String> labels = new ArrayList<>(SEQUENCE_LABELS.subList(0, count));
        Collections.shuffle(labels, random);
        return labels;
    }
}

// ──────────────────────────────────────────────
// 로그인 인증 컨트롤러
//
// 사이트에 처음 접속할 때만 로그인 창을 표시하기 위해
// HTTP 세션으로 로그인 상태를 관리한다.
//
// 흐름:
//   1. 페이지 로드 시 GET /api/auth/status 호출
//      → loggedIn: false 이면 로그인 창 표시
//      → loggedIn: true  이면 바로 메인 화면으로 진입
//   2. 로그인 창에서 POST /api/auth/login 호출
//      → 성공 시 세션에 사용자 정보 저장, 이후 접속부터는 창 미표시
//      → 실패 시 401 반환, 로그인 창 유지
//   3. POST /api/auth/logout 호출 시 세션 삭제
//      → 다음 접속 시 다시 로그인 창 표시
// ──────────────────────────────────────────────
@RestController
@RequestMapping("/api/auth")
class AuthController {

    // 세션에 저장할 사용자 정보의 키
    private static final String SESSION_USER_KEY = "loggedInUser";

    // 사용할 수 있는 계정 목록 (아이디 → 비밀번호)
    // 실제 서비스에서는 DB 연동으로 교체해야 한다.
    private static final Map<String, String> USER_DB = Map.of(
            "student1", "pass1234",
            "student2", "pass1234",
            "admin",    "admin1234"
    );

    // GET /api/auth/status
    // 현재 세션에 로그인 정보가 있는지 확인한다.
    // 프론트엔드가 페이지 로드 직후 이 API를 호출하여
    // 로그인 창 표시 여부를 결정한다.
    //
    // 응답 예시 (로그인됨):  { "loggedIn": true,  "username": "student1" }
    // 응답 예시 (미로그인):  { "loggedIn": false, "username": null }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkStatus(HttpSession session) {

        // 세션에서 로그인된 사용자 이름을 꺼낸다
        String username = (String) session.getAttribute(SESSION_USER_KEY);
        boolean loggedIn = username != null;

        Map<String, Object> result = new HashMap<>();
        result.put("loggedIn", loggedIn);
        result.put("username", username);

        return ResponseEntity.ok(result);
    }

    // POST /api/auth/login
    // 아이디·비밀번호를 검증하고, 성공 시 세션에 사용자 정보를 저장한다.
    // 세션이 생성되면 이후 접속에서는 로그인 창이 뜨지 않는다.
    //
    // 요청 body: { "username": "student1", "password": "pass1234" }
    // 성공 응답: { "success": true, "username": "student1" }
    // 실패 응답: { "success": false, "message": "..." }  (HTTP 401)
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> credentials,
            HttpSession session) {

        String username = credentials.get("username");
        String password = credentials.get("password");

        Map<String, Object> result = new HashMap<>();

        // 아이디와 비밀번호가 USER_DB와 일치하는지 확인
        if (username != null
                && USER_DB.containsKey(username)
                && USER_DB.get(username).equals(password)) {

            // 로그인 성공 → 세션에 사용자 이름 저장
            session.setAttribute(SESSION_USER_KEY, username);

            result.put("success", true);
            result.put("username", username);
            result.put("message", "로그인 성공");
            return ResponseEntity.ok(result);

        } else {
            // 로그인 실패 → 401 반환, 세션에 아무것도 저장하지 않음
            result.put("success", false);
            result.put("message", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }

    // POST /api/auth/logout
    // 현재 세션을 무효화하여 로그아웃 처리한다.
    // 이후 접속 시 GET /api/auth/status 가 loggedIn: false 를 반환하므로
    // 다시 로그인 창이 표시된다.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {

        // 세션 전체를 삭제하여 로그인 상태 초기화
        session.invalidate();

        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }
}

// ──────────────────────────────────────────────
// 수강신청 성공·실패 처리 컨트롤러
//
// 수강신청 버튼 클릭 → 기믹 완료 → 이 API 호출의 흐름으로 동작한다.
// 아래 5단계를 순서대로 검증하며, 하나라도 실패하면 즉시 실패 이유와 함께 반환한다.
//
//   1. 로그인 여부        → NOT_LOGGED_IN
//   2. 수강신청 시간(10시) → TIME_NOT_OPEN
//   3. 과목 여석          → NO_SEATS
//   4. 중복 신청 여부      → ALREADY_ENROLLED
//   5. 기믹 정답 검증      → GIMMICK_NOT_STARTED / GIMMICK_FAILED
//   → 전부 통과           → SUCCESS
// ──────────────────────────────────────────────
@RestController
@RequestMapping("/api/enroll")
class EnrollmentController {

    // 실패 이유 코드 상수 (프론트엔드가 이 값으로 어떤 창을 띄울지 결정한다)
    private static final String REASON_NOT_LOGGED_IN      = "NOT_LOGGED_IN";
    private static final String REASON_TIME_NOT_OPEN      = "TIME_NOT_OPEN";
    private static final String REASON_NO_SEATS           = "NO_SEATS";
    private static final String REASON_ALREADY_ENROLLED   = "ALREADY_ENROLLED";
    private static final String REASON_GIMMICK_NOT_STARTED = "GIMMICK_NOT_STARTED";
    private static final String REASON_GIMMICK_FAILED     = "GIMMICK_FAILED";

    // 수강신청 완료 목록: 과목명 → 신청한 사용자 이름 Set
    // ConcurrentHashMap·newKeySet으로 동시 요청에도 안전하게 처리한다.
    private static final Map<String, Set<String>> ENROLLED = new ConcurrentHashMap<>();

    // POST /api/enroll/attempt
    // 수강신청을 최종 시도한다. 5단계 검증 후 성공/실패 결과를 반환한다.
    //
    // 요청 body:
    //   { "subjectName": "알고리즘", "gimmickAnswer": "A3KM9R" }
    //   TYPE_CODE   → gimmickAnswer: 입력한 코드 문자열
    //   MATH_QUIZ   → gimmickAnswer: 계산 결과 숫자 (문자열도 가능, 서버에서 파싱)
    //   SLIDER      → gimmickAnswer: 슬라이더 위치 숫자
    //   CLICK_COUNT·BUTTON_SEQUENCE → gimmickAnswer: "COMPLETED" (클라이언트 완료 보고)
    //
    // 성공 응답: { "success": true,  "subjectName": "알고리즘", "message": "..." }
    // 실패 응답: { "success": false, "failReason": "NO_SEATS",  "message": "..." }
    @PostMapping("/attempt")
    public ResponseEntity<Map<String, Object>> attempt(
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        String subjectName = (String) body.get("subjectName");
        Object userAnswer  = body.get("gimmickAnswer");
        String username    = (String) session.getAttribute("loggedInUser");

        // 공통 응답 베이스 (항상 과목명을 포함)
        Map<String, Object> result = new HashMap<>();
        result.put("subjectName", subjectName);

        // ── 1단계: 로그인 여부 ──────────────────────────
        if (username == null) {
            return buildFail(result, REASON_NOT_LOGGED_IN, "로그인이 필요합니다.");
        }

        // ── 2단계: 수강신청 시간 확인 (서버 시각 기준) ──
        if (LocalTime.now().isBefore(LocalTime.of(10, 0, 0))) {
            return buildFail(result, REASON_TIME_NOT_OPEN, "수강신청 시간(10:00:00)이 아닙니다.");
        }

        // ── 3단계: 여석(타이머) 확인 ─────────────────────
        if (!hasSeats(subjectName)) {
            return buildFail(result, REASON_NO_SEATS, "수강 여석이 없습니다.");
        }

        // ── 4단계: 중복 신청 확인 ────────────────────────
        if (ENROLLED.getOrDefault(subjectName, Set.of()).contains(username)) {
            return buildFail(result, REASON_ALREADY_ENROLLED, "이미 신청한 과목입니다.");
        }

        // ── 5단계: 기믹 정답 검증 ────────────────────────
        @SuppressWarnings("unchecked")
        Map<String, Object> gimmickSession =
                (Map<String, Object>) session.getAttribute("gimmick_" + subjectName);

        // 기믹 자체가 발급된 적 없으면 정상 경로가 아님
        if (gimmickSession == null) {
            return buildFail(result, REASON_GIMMICK_NOT_STARTED,
                    "기믹이 시작되지 않았습니다. 수강신청 버튼을 먼저 눌러주세요.");
        }

        if (!verifyGimmick(gimmickSession, userAnswer)) {
            return buildFail(result, REASON_GIMMICK_FAILED, "기믹 인증에 실패했습니다.");
        }

        // ── 모든 검증 통과 → 수강신청 성공 ──────────────
        // 기믹 토큰 소비 (재사용 방지)
        session.removeAttribute("gimmick_" + subjectName);

        // 수강신청 완료 목록에 등록
        ENROLLED.computeIfAbsent(subjectName, k -> ConcurrentHashMap.newKeySet()).add(username);

        result.put("success", true);
        result.put("username", username);
        result.put("message", subjectName + " 수강신청이 완료되었습니다!");
        return ResponseEntity.ok(result);
    }

    // GET /api/enroll/my
    // 현재 로그인한 사용자가 수강신청에 성공한 과목 목록을 반환한다.
    // 응답 예시: { "username": "student1", "enrolledSubjects": ["알고리즘", "네트워크"], "count": 2 }
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> myEnrollments(HttpSession session) {
        String username = (String) session.getAttribute("loggedInUser");

        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "로그인이 필요합니다."));
        }

        // 전체 수강신청 목록에서 내 이름이 포함된 과목만 필터링
        List<String> mySubjects = ENROLLED.entrySet().stream()
                .filter(entry -> entry.getValue().contains(username))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("username", username);
        result.put("enrolledSubjects", mySubjects);
        result.put("count", mySubjects.size());
        return ResponseEntity.ok(result);
    }

    // GET /api/enroll/status/{subjectName}
    // 특정 과목의 신청 완료 인원 수를 반환한다.
    // 응답 예시: { "subjectName": "알고리즘", "enrolledCount": 3 }
    @GetMapping("/status/{subjectName}")
    public ResponseEntity<Map<String, Object>> subjectStatus(
            @PathVariable String subjectName) {

        Set<String> enrolled = ENROLLED.getOrDefault(subjectName, Set.of());

        Map<String, Object> result = new HashMap<>();
        result.put("subjectName", subjectName);
        result.put("enrolledCount", enrolled.size());
        return ResponseEntity.ok(result);
    }

    // 과목의 여석 여부를 확인한다.
    // SubjectTimerController의 static 데이터를 참조하여
    // 경과 시간이 제한 시간보다 작을 때만 여석 있음으로 판단한다.
    private boolean hasSeats(String subjectName) {
        LocalDateTime startTime = SubjectTimerController.startTimes.get(subjectName);
        Integer duration = SubjectTimerController.SUBJECT_DURATIONS.get(subjectName);
        if (startTime == null || duration == null) return false;
        long elapsed = Duration.between(startTime, LocalDateTime.now()).getSeconds();
        return elapsed < duration;
    }

    // 기믹 유형별로 사용자 답변과 정답을 비교하여 정오를 반환한다.
    private boolean verifyGimmick(Map<String, Object> gimmickSession, Object userAnswer) {
        if (userAnswer == null) return false;

        String type          = (String) gimmickSession.get("type");
        Object correctAnswer = gimmickSession.get("correctAnswer");

        return switch (type) {
            // 코드 타이핑: 대소문자 구분하여 정확히 일치해야 함
            case "TYPE_CODE" ->
                    userAnswer.toString().equals(correctAnswer.toString());

            // 수학 문제: 정수 값이 일치해야 함
            case "MATH_QUIZ" -> {
                try {
                    int ua = Integer.parseInt(userAnswer.toString().trim());
                    int ca = ((Number) correctAnswer).intValue();
                    yield ua == ca;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }

            // 슬라이더: 허용 오차(±5%) 이내면 성공
            case "SLIDER" -> {
                try {
                    int ua = Integer.parseInt(userAnswer.toString().trim());
                    int ca = ((Number) correctAnswer).intValue();
                    yield Math.abs(ua - ca) <= 5;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }

            // CLICK_COUNT·BUTTON_SEQUENCE: 클라이언트가 "COMPLETED" 보고 시 성공
            default -> "COMPLETED".equals(userAnswer.toString());
        };
    }

    // 실패 응답을 일관된 형식으로 생성하는 헬퍼 메서드
    private ResponseEntity<Map<String, Object>> buildFail(
            Map<String, Object> base, String reason, String message) {
        base.put("success", false);
        base.put("failReason", reason);
        base.put("message", message);
        return ResponseEntity.ok(base);
    }
}
