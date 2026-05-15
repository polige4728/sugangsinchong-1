let startTime = 0;
let timerInterval = null;
let gameLimitTimer = null;
let randomBackgroundGimmickTimer = null;

let isGameStarted = false;
let isGameOver = false;

let courseCount = 0;
let creditCount = 0;

let selectedCourse = null;
let selectedButton = null;
let currentMacroCode = "";
let confirmStep = 0;

let timeGimmickStart = 0;
let timeGimmickInterval = null;
let isMouseReverse = false;
let fakeCursor = null;

const GAME_LIMIT_SECONDS = 60;

const courses = [
    { subjectCode: "009914", classNumber: "001", department: "컴퓨터공학과", courseName: "공학설계기초", credit: "3.0/3/0", courseType: "전선", grade: "2", schedule: "금 09:00-12:00" },
    { subjectCode: "009952", classNumber: "004", department: "컴퓨터공학과", courseName: "자료구조및실습", credit: "3.0/3/0", courseType: "전필", grade: "2", schedule: "화목 14:00-16:00" },
    { subjectCode: "011320", classNumber: "001", department: "대양휴머니티칼리지", courseName: "인공지능과빅데이터", credit: "3.0/2/1", courseType: "기필", grade: "2", schedule: "" },
    { subjectCode: "011238", classNumber: "001", department: "대양휴머니티칼리지", courseName: "우주자연인간", credit: "1.0/1/0", courseType: "공필", grade: "1", schedule: "" },
    { subjectCode: "004118", classNumber: "002", department: "컴퓨터공학과", courseName: "디지털시스템", credit: "3.0/3/0", courseType: "전필", grade: "2", schedule: "화목 12:00-14:00" },
    { subjectCode: "007330", classNumber: "002", department: "컴퓨터공학과", courseName: "확률및통계", credit: "3.0/3/0", courseType: "전기", grade: "2", schedule: "월수 13:30-15:00" }
];

const timer = document.getElementById("timer");
const startBtn = document.getElementById("startBtn");
const refreshBtn = document.getElementById("refreshBtn");

const courseTable = document.getElementById("courseTable");
const selectedCourses = document.getElementById("selectedCourses");

const courseCountText = document.getElementById("courseCount");
const creditCountText = document.getElementById("creditCount");

const macroModal = document.getElementById("macroModal");
const macroNumber = document.getElementById("macroNumber");
const macroInput = document.getElementById("macroInput");
const macroSubmitBtn = document.getElementById("macroSubmitBtn");
const macroCancelBtn = document.getElementById("macroCancelBtn");
const macroCloseBtn = document.getElementById("macroCloseBtn");

const confirmModal = document.getElementById("confirmModal");
const confirmMessage = document.getElementById("confirmMessage");
const confirmCourseName = document.getElementById("confirmCourseName");
const confirmOkBtn = document.getElementById("confirmOkBtn");
const confirmCancelBtn = document.getElementById("confirmCancelBtn");
const confirmCloseBtn = document.getElementById("confirmCloseBtn");

const timerGimmickModal = document.getElementById("timerGimmickModal");
const timeGimmickNumber = document.getElementById("timeGimmickNumber");
const timeStopBtn = document.getElementById("timeStopBtn");
const timeGimmickCloseBtn = document.getElementById("timeGimmickCloseBtn");

window.addEventListener("load", function () {
    courseTable.innerHTML = "";
    selectedCourses.innerHTML = "";

    for (let i = 0; i < 4; i++) {
        const emptyRow = document.createElement("tr");

        emptyRow.innerHTML = `
            <td>&nbsp;</td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
            <td></td>
        `;

        courseTable.appendChild(emptyRow);
    }

    updateSummary();
});

startBtn.addEventListener("click", function () {
    if (isGameStarted) return;

    isGameStarted = true;
    isGameOver = false;
    startTime = Date.now();

    courseCount = 0;
    creditCount = 0;

    selectedCourses.innerHTML = "";
    updateSummary();
    renderRandomCourses();

    startBtn.textContent = "진행 중";
    startBtn.disabled = true;

    timerInterval = setInterval(function () {
        const elapsedTime = (Date.now() - startTime) / 1000;
        const remainTime = GAME_LIMIT_SECONDS - elapsedTime;

        if (remainTime <= 0) {
            timer.textContent = "00.00초";
            gameOver();
            return;
        }

        timer.textContent = remainTime.toFixed(2) + "초";
    }, 10);

    gameLimitTimer = setTimeout(function () {
        gameOver();
    }, GAME_LIMIT_SECONDS * 1000);

    startRandomBackgroundGimmicks();
});

function renderRandomCourses() {
    const shuffledCourses = [...courses].sort(function () {
        return Math.random() - 0.5;
    });

    courseTable.innerHTML = "";

    shuffledCourses.forEach(function (course, index) {
        const row = document.createElement("tr");

        row.innerHTML = `
            <td>${index + 1}</td>
            <td><button class="apply-btn">신청</button></td>
            <td>${course.subjectCode}</td>
            <td>${course.classNumber}</td>
            <td>${course.department}</td>
            <td>${course.courseName}</td>
            <td><button class="mini-btn">수업계획서</button></td>
            <td></td>
            <td>${course.credit}</td>
            <td>${course.courseType}</td>
            <td>${course.grade}</td>
            <td>${course.schedule}</td>
            <td><button class="mini-btn">인원보기</button></td>
        `;

        const applyBtn = row.querySelector(".apply-btn");

        applyBtn.addEventListener("click", function () {
            startRandomApplyGimmick(course, applyBtn);
        });

        courseTable.appendChild(row);
    });
}

function startRandomApplyGimmick(course, button) {
    if (!isGameStarted || isGameOver) {
        alert("게임이 진행 중이 아닙니다.");
        return;
    }

    selectedCourse = course;
    selectedButton = button;

    const gimmicks = [
        openMacroModal,
        openLongMacroModal,
        openTimeGimmick
    ];

    const randomIndex = Math.floor(Math.random() * gimmicks.length);
    gimmicks[randomIndex]();
}

/* 수강신청 중 랜덤 배경 기믹 */
function startRandomBackgroundGimmicks() {
    randomBackgroundGimmickTimer = setInterval(function () {
        if (!isGameStarted || isGameOver) return;

        const random = Math.random();

        if (random < 0.5) {
            startScreenInvert();
        } else {
            startMouseReverse();
        }
    }, 12000);
}

/* 기본 매크로 */
function openMacroModal() {
    currentMacroCode = makeRandomCode(4);

    macroNumber.textContent = currentMacroCode;
    macroInput.value = "";

    resetMacroStyle();

    macroModal.style.display = "flex";
}

/* 긴 매크로: 10~20자 */
function openLongMacroModal() {
    const length = Math.floor(Math.random() * 11) + 10;

    currentMacroCode = makeRandomCode(length);
    macroNumber.textContent = currentMacroCode;

    if (length <= 12) {
        macroNumber.style.fontSize = "48px";
        macroNumber.style.letterSpacing = "6px";
    } else if (length <= 16) {
        macroNumber.style.fontSize = "38px";
        macroNumber.style.letterSpacing = "4px";
    } else {
        macroNumber.style.fontSize = "30px";
        macroNumber.style.letterSpacing = "2px";
    }

    macroNumber.style.whiteSpace = "nowrap";
    macroNumber.style.overflow = "hidden";

    macroInput.value = "";
    macroModal.style.display = "flex";
}

function makeRandomCode(length) {
    const chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    let result = "";

    for (let i = 0; i < length; i++) {
        result += chars[Math.floor(Math.random() * chars.length)];
    }

    return result;
}

macroSubmitBtn.addEventListener("click", function () {
    if (isGameOver) return;

    if (macroInput.value.trim().toUpperCase() !== currentMacroCode) {
        alert("매크로 번호가 일치하지 않습니다.");
        macroInput.value = "";
        return;
    }

    macroModal.style.display = "none";
    resetMacroStyle();
    openConfirmStep1();
});

macroCancelBtn.addEventListener("click", closeMacroModal);
macroCloseBtn.addEventListener("click", closeMacroModal);

function closeMacroModal() {
    macroModal.style.display = "none";
    resetMacroStyle();
}

/* 3초 정확히 맞추기 */
function openTimeGimmick() {
    timerGimmickModal.style.display = "flex";
    timeGimmickStart = Date.now();

    clearInterval(timeGimmickInterval);

    timeGimmickInterval = setInterval(function () {
        const elapsed = (Date.now() - timeGimmickStart) / 1000;
        timeGimmickNumber.textContent = elapsed.toFixed(2).replace(".", ":");
    }, 10);
}

timeStopBtn.addEventListener("click", function () {
    if (isGameOver) return;

    const elapsed = (Date.now() - timeGimmickStart) / 1000;

    clearInterval(timeGimmickInterval);

    if (elapsed >= 2.95 && elapsed <= 3.05) {
        timerGimmickModal.style.display = "none";
        openConfirmStep1();
    } else {
        alert("실패했습니다. 3초에 정확히 맞춰 다시 시도하세요.");
        openTimeGimmick();
    }
});

timeGimmickCloseBtn.addEventListener("click", closeTimeGimmick);

function closeTimeGimmick() {
    clearInterval(timeGimmickInterval);
    timerGimmickModal.style.display = "none";
}

/* 화면 반전 10초 */
function startScreenInvert() {
    if (document.body.classList.contains("screen-invert")) return;

    document.body.classList.add("screen-invert");

    setTimeout(function () {
        document.body.classList.remove("screen-invert");
    }, 10000);
}

/* 마우스 반전 10초 */
function startMouseReverse() {
    if (isMouseReverse) return;

    isMouseReverse = true;
    document.body.style.cursor = "none";

    fakeCursor = document.createElement("div");
    fakeCursor.className = "fake-cursor";
    document.body.appendChild(fakeCursor);

    document.addEventListener("mousemove", reverseMouseMove);

    setTimeout(function () {
        endMouseReverse();
    }, 10000);
}

function reverseMouseMove(event) {
    if (!fakeCursor) return;

    const x = window.innerWidth - event.clientX;
    const y = window.innerHeight - event.clientY;

    fakeCursor.style.left = x + "px";
    fakeCursor.style.top = y + "px";
}

function endMouseReverse() {
    document.removeEventListener("mousemove", reverseMouseMove);
    document.body.style.cursor = "default";

    if (fakeCursor) {
        fakeCursor.remove();
        fakeCursor = null;
    }

    isMouseReverse = false;
}

/* 수강신청 확인창 */
function openConfirmStep1() {
    if (isGameOver) return;

    confirmStep = 1;

    confirmMessage.innerHTML = `
        선택한 과목을 수강신청 하시겠습니까?<br><br>
        교과목명(Course Title)
    `;

    confirmCourseName.textContent = selectedCourse.courseName;

    randomizeConfirmButtonOrder();

    confirmModal.style.display = "flex";
}

function openConfirmStep2() {
    if (isGameOver) return;

    confirmStep = 2;

    confirmMessage.innerHTML = `
        과목이 신청 되었습니다. 수강신청내역을 재 조회 하시겠습니까?<br><br>
        ※ 취소를 선택하실 경우 [수강신청내역]이 갱신되지 않습니다.<br><br>
        취소를 선택하실 경우 수강신청 최종 완료 후 반드시 [수강신청내역]
        재조회를 눌러 신청내역을 확인하세요.
    `;

    confirmCourseName.textContent = "";

    randomizeConfirmButtonOrder();

    confirmModal.style.display = "flex";
}

function randomizeConfirmButtonOrder() {
    if (Math.random() < 0.5) {
        confirmOkBtn.style.order = "2";
        confirmCancelBtn.style.order = "1";
    } else {
        confirmOkBtn.style.order = "1";
        confirmCancelBtn.style.order = "2";
    }
}

function resetConfirmButtons() {
    confirmOkBtn.style.order = "1";
    confirmCancelBtn.style.order = "2";
}

confirmOkBtn.addEventListener("click", function () {
    if (isGameOver) return;

    if (confirmStep === 1) {
        openConfirmStep2();
    } else if (confirmStep === 2) {
        confirmOkBtn.disabled = true;
        confirmOkBtn.textContent = "처리중";

        setTimeout(function () {
            confirmModal.style.display = "none";
            confirmOkBtn.disabled = false;
            confirmOkBtn.textContent = "확인";
            resetConfirmButtons();

            applyCourse(selectedCourse, selectedButton);

            selectedCourse = null;
            selectedButton = null;
            currentMacroCode = "";
            confirmStep = 0;
        }, 2000);
    }
});

confirmCancelBtn.addEventListener("click", closeConfirmModal);
confirmCloseBtn.addEventListener("click", closeConfirmModal);

function closeConfirmModal() {
    confirmModal.style.display = "none";
    resetConfirmButtons();
    confirmStep = 0;
}

/* 실제 수강신청 처리 */
function applyCourse(course, button) {
    if (isGameOver) return;

    courseCount++;

    const creditValue = parseFloat(course.credit);
    creditCount += creditValue;

    const newRow = document.createElement("tr");

    newRow.innerHTML = `
        <td>${courseCount}</td>
        <td><button class="delete-btn">삭제</button></td>
        <td>${course.subjectCode}</td>
        <td>${course.classNumber}</td>
        <td>${course.department}</td>
        <td>${course.courseName}</td>
        <td><button class="mini-btn">수업계획서</button></td>
        <td></td>
        <td>${course.credit}</td>
        <td>${course.courseType}</td>
        <td>-</td>
        <td>${course.schedule}</td>
        <td><button class="mini-btn">인원보기</button></td>
    `;

    selectedCourses.appendChild(newRow);

    button.disabled = true;
    button.textContent = "완료";

    updateSummary();

    const deleteBtn = newRow.querySelector(".delete-btn");

    deleteBtn.addEventListener("click", function () {
        if (isGameOver) return;

        newRow.remove();

        courseCount--;
        creditCount -= creditValue;

        button.disabled = false;
        button.textContent = "신청";

        updateSelectedCourseNumbers();
        updateSummary();
    });

    checkClear();
}

function updateSelectedCourseNumbers() {
    const rows = selectedCourses.querySelectorAll("tr");

    rows.forEach(function (row, index) {
        row.children[0].textContent = index + 1;
    });
}

function updateSummary() {
    courseCountText.textContent = courseCount;
    creditCountText.textContent = creditCount;
}

function checkClear() {
    const applyButtons = document.querySelectorAll(".apply-btn");
    let completedCount = 0;

    applyButtons.forEach(function (button) {
        if (button.disabled) {
            completedCount++;
        }
    });

    if (completedCount === courses.length) {
        gameClear();
    }
}

function gameClear() {
    clearInterval(timerInterval);
    clearTimeout(gameLimitTimer);
    clearInterval(randomBackgroundGimmickTimer);

    const clearTime = (Date.now() - startTime) / 1000;

    setTimeout(function () {
        alert("수강신청 성공! 기록: " + clearTime.toFixed(2) + "초");
    }, 100);

    isGameStarted = false;
}

function gameOver() {
    if (isGameOver) return;

    isGameOver = true;
    isGameStarted = false;

    clearInterval(timerInterval);
    clearTimeout(gameLimitTimer);
    clearInterval(randomBackgroundGimmickTimer);
    clearInterval(timeGimmickInterval);

    macroModal.style.display = "none";
    confirmModal.style.display = "none";
    timerGimmickModal.style.display = "none";

    document.body.classList.remove("screen-invert");
    endMouseReverse();

    const applyButtons = document.querySelectorAll(".apply-btn");
    applyButtons.forEach(function (button) {
        button.disabled = true;
    });

    startBtn.textContent = "게임 오버";
    startBtn.disabled = true;

    alert("게임 오버! 제한시간 1분 안에 수강신청을 완료하지 못했습니다.");
}

refreshBtn.addEventListener("click", function () {
    location.reload();
});