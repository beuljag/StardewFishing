# StardewFishing

스타듀 밸리 방식의 낚시 미니게임을 추가하는 Paper 1.21.1 플러그인입니다.

- 쉬프트로 막대를 움직여 물고기를 잡는 미니게임
- 낚시터를 여러 개 지정하고, 낚시터마다 나오는 물고기와 확률을 설정
- 낚시대마다 막대 크기, 입질 속도, 파괴 확률을 설정
- ItemsAdder 연동으로 낚시대와 물고기의 외형을 지정 (선택 사항)

> **테스트 상태:** 서버 로드, 콘솔 명령어, 미니게임 물리 시뮬레이션까지 확인했습니다. 실제 접속 상태의 낚시와 ItemsAdder 연동은 아직 시험하지 않았습니다.

## 설치

1. `StardewFishing.jar`를 서버의 `plugins` 폴더에 넣습니다.
2. 서버를 재시작하면 `plugins/StardewFishing`에 설정 파일이 생깁니다.

ItemsAdder는 없어도 작동합니다. ItemsAdder 외형을 쓸 때만 필요합니다.

## 플레이 방법

1. 낚시터에 낚싯대를 던집니다.
2. 화면에 **입질!** 이 뜨면 바로 우클릭합니다.
3. 미니게임이 시작되면 **쉬프트를 누르고 있어** 막대를 올리고, 떼서 내립니다.
4. 물고기를 막대 안에 두면 게이지가 차고, 벗어나면 줄어듭니다.
5. 게이지가 가득 차면 물고기를 얻고, 다 줄어들면 도망갑니다.

미니게임 중에 다른 아이템으로 바꾸거나 찌와 너무 멀어지면 낚시가 중단되고 보상은 없습니다.

## 낚시터 만들기

물을 바라본 상태에서 두 모서리를 지정합니다. 수면 높이가 구역 안에 들어가야 합니다.

```
/낚시 pos1
/낚시 pos2
/낚시 zone create lake
/낚시 zone addfish lake cod 60
/낚시 zone addfish lake salmon 30
/낚시 zone addfish lake pufferfish 10
```

마지막 숫자는 가중치이고, 확률은 **자기 숫자 ÷ 그 낚시터 숫자 전체의 합**입니다. 위 예시는 대구 60%, 연어 30%, 복어 10%입니다.

- 낚시터는 여러 개 만들 수 있고, 겹치면 더 작은 구역이 적용됩니다.
- 물고기를 넣지 않은 낚시터에서는 바닐라 낚시가 됩니다.
- 이 확률은 입질 때 어떤 물고기가 걸릴지를 정합니다. 미니게임에 실패하면 그 물고기는 얻지 못합니다.

## 명령어

`/낚시`와 `/fishing`은 같은 명령어입니다. 권한은 `stardewfishing.admin`이고, 기본적으로 OP만 쓸 수 있습니다.

| 명령어 | 설명 |
|---|---|
| `/낚시 pos1`, `/낚시 pos2` | 바라보는 블록을 구역 모서리로 지정 |
| `/낚시 zone create <낚시터>` | 낚시터 생성 (같은 이름이면 구역만 변경) |
| `/낚시 zone delete <낚시터>` | 낚시터 삭제 |
| `/낚시 zone addfish <낚시터> <물고기> <가중치>` | 물고기 추가 또는 확률 변경 |
| `/낚시 zone removefish <낚시터> <물고기>` | 물고기 제거 |
| `/낚시 zone list` | 낚시터 목록 |
| `/낚시 zone info <낚시터>` | 구역과 물고기별 확률 확인 |
| `/낚시 rod list` | 낚시대 목록 |
| `/낚시 rod give <낚시대> [플레이어]` | 낚시대 지급 |
| `/낚시 fish list` | 물고기 목록 |
| `/낚시 fish give <물고기> [플레이어]` | 물고기 지급 |
| `/낚시 fish add <ID> <난이도> <움직임> [경험치]` | 손에 든 아이템을 물고기로 등록 |
| `/낚시 reload` | 설정 파일 다시 불러오기 |

## 설정

### 낚시대: `rods.yml`

```yaml
rods:
  iron:
    bar-size: 30          # 막대 크기(%). 5~100, 클수록 쉬움
    bite-time: {min: 60, max: 240}   # 입질 대기 시간(틱). 20틱 = 1초
    break-chance: 1.0     # 파괴 확률(%). 미니게임이 끝날 때마다 판정
    item:
      material: FISHING_ROD
      name: "&b강철 낚시대"
      lore:
        - "&7막대가 넓고 입질이 빠르다"
```

`bite-time`이 지난 뒤 물고기가 찌로 다가오는 데 1~4초가 더 걸립니다. 플러그인으로 지급한 낚시대는 내구도가 닳지 않고 파괴 확률로만 부서집니다.

### 물고기: `fish.yml`

```yaml
fish:
  salmon:
    difficulty: 40    # 쉬움 15 ~ 전설 110
    motion: SMOOTH
    exp: 5
    item:
      material: SALMON
      name: "&c연어"
```

| 움직임 | 특징 |
|---|---|
| `MIXED` | 섞어서 움직임 |
| `SMOOTH` | 부드럽게 오르내림 |
| `DART` | 순간적으로 크게 튐 |
| `SINKER` | 아래로 가라앉음 |
| `FLOATER` | 위로 떠오름 |

### 낚시터: `zones.yml`

보통은 명령어로 만들지만 파일에서 직접 고칠 수도 있습니다.

```yaml
zones:
  lake:
    world: world
    min: {x: 100, y: 60, z: 100}
    max: {x: 150, y: 64, z: 150}
    fish:
      cod: 60
      salmon: 30
      pufferfish: 10
```

### 기타: `config.yml`

| 항목 | 설명 |
|---|---|
| `vanilla-rod` | 일반 낚시대가 낚시터에서 쓸 능력치 (기본 `basic`). 비우면 바닐라 낚시 |
| `allow-vanilla-outside-zones` | `false`면 낚시터 밖에서 물고기를 낚을 수 없음 |
| `display.mode` | `TEXT_DISPLAY`(세로 막대) 또는 `ACTIONBAR`(가로 막대) |
| `display.rows`, `scale`, `distance`, `side-offset` | 막대의 칸 수, 크기, 거리, 옆 위치 |
| `messages` | 게임 메시지 문구. `&` 색상 코드 사용 가능 |

파일을 고친 뒤에는 `/낚시 reload`를 입력하면 적용됩니다.

## ItemsAdder 연동

`item` 항목을 아래처럼 쓰면 낚시대와 물고기 모두 ItemsAdder 외형을 씁니다.

```yaml
item:
  itemsadder: "네임스페이스:아이템ID"
```

- 낚시대는 ItemsAdder 쪽에서 재료를 반드시 `FISHING_ROD`로 만들어야 던질 수 있습니다.
- ItemsAdder 명령어나 조합으로 얻은 낚시대도 ID가 같으면 같은 능력치가 적용됩니다.
- ItemsAdder 없이 `material: FISHING_ROD`와 `custom-model-data: 번호`로 리소스팩 모델을 지정할 수도 있습니다.

## 알아 둘 점

- 낚시터 안에서는 바다의 행운 인챈트가 적용되지 않습니다. 미끼는 입질 시간을 그대로 줄여 줍니다.
- 일반 낚시대도 낚시터 안에서는 `vanilla-rod` 낚시대의 파괴 확률이 적용됩니다.
- 낚시터 밖의 커스텀 낚시대는 바닐라 낚시를 하지만 입질 속도는 낚시대 설정을 따릅니다.

## 빌드

JDK 21 이상이 필요합니다. `build.bat`은 저장소가 아래 위치에 있고, Paper 1.21.1을 한 번 실행해 `libraries` 폴더가 만들어진 서버를 기준으로 컴파일합니다.

```
서버/
├─ 1.20.1/                 Paper 서버 (libraries 사용)
└─ 플러그인/StardewFishing/  이 저장소
```

```bat
build.bat
```

결과물은 `build/StardewFishing.jar`입니다.
