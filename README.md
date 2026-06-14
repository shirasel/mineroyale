# MineRoyale

Minecraft 上でバトルロワイヤル形式の試合を行う Paper プラグインです。
プレイヤーはランダムな位置にスポーンし、時間経過で縮小するワールドボーダー内で最後の1人を目指します。

## 対応環境

- Server: Paper 26.1.2 想定
- Java: 25
- Plugin name: `mineroyale`
- Main command: `/mr`

## 主な機能

- `/mr start` でカウントダウン後に試合開始
- 参加者をランダムな安全地点へテレポート
- オークの木材 1 スタックを初期配布
- 設定により初期コンパスを配布
- 現在の初期設定では、生存者が4人以下になるとコンパスが最寄りの生存者を追跡
- 試合開始時に「発光の岩」を生存者全員へ1つずつ配布
- 発光の岩を右クリックすると、自分以外の生存者1名をランダムに15秒間発光
- PvP 猶予時間あり
- ワールドボーダーの段階的収縮
- 最終フェーズではボーダー中心がランダムに揺れ続け、徐々に移動速度が上昇
- ボーダー外の継続ダメージ
- 現在の初期設定では、ボーダー外でのブロック設置/破壊を制限
- 死亡したプレイヤーは観戦モードへ移行
- 観戦者はブレイズロッド右クリックまたは `/spec` で観戦先 GUI を開ける
- 観戦先 GUI 内の生存者の頭をクリックすると、そのプレイヤーへテレポート
- 死亡地点にプレイヤー頭付き防具立てを生成
- 次ゲーム開始時・プラグイン起動時に死亡マーカーを自動削除
- ネームタグ非表示、実績通知OFF、プレイヤーロケーターバー表示を config で制御

## コマンド

| コマンド | 説明 | 権限 |
| --- | --- | --- |
| `/mr start` | 試合開始カウントダウンを開始 | `mineroyale.admin` |
| `/mr stop` | 実行中の試合を停止 | `mineroyale.admin` |
| `/mr reload` | config を再読み込み | `mineroyale.admin` |
| `/spec` | 観戦者用のテレポート GUI を開く | なし |

`mineroyale.admin` はデフォルトで OP に付与されます。

## 基本の流れ

1. サーバに必要人数が参加します。
2. 管理者が `/mr start` を実行します。
3. カウントダウン中にスポーン地点を事前生成します。
4. チャンク先読み後、参加者をランダムな安全地点へテレポートします。
5. PvP 猶予時間後に戦闘が有効になります。
6. ボーダーが段階的に縮小します。
7. 最終フェーズではボーダー中心が揺れ続けます。
8. 最後の1人が勝者になります。

## ルール

- Minecraft でバトルロワイヤルを行います。
- 基本的には通常の Minecraft アイテム仕様に従います。
- 試合開始時にコンパスが配布されます。
- コンパスは、生存者が設定人数以下になったとき、最も近い生存プレイヤーを指します。
- コンパスを捨てても再配布はされません。
- 試合開始時に「発光の岩」が生存者全員に1つずつ配布されます。
- 発光の岩は右クリックで使用できます。
- 使用すると、自分以外の生存者からランダムで1人を15秒間発光状態にします。
- 空腹にはなりません。
- 最後の1人になると試合終了です。
- エリアは時間経過で収縮します。
- エリア外ではダメージを受けます。
- 現在の初期設定では、エリア外でのブロック設置/破壊はできません。
- 倒されると Spectator Mode（観戦）になります。
- 観戦中はブレイズロッド右クリック、または `/spec` で生存者の頭一覧 GUI を開けます。
- GUI 内の生存者の頭をクリックすると、そのプレイヤーへテレポートできます。

## 主要設定

```yaml
game:
  min-players: 2
  max-players: 20
  countdown-seconds: 30
  initial-pvp-grace-seconds: 45
  show-player-locator-bar: false
  enable-compass-tracking: true
  player-locator-max-alive-players: 4
  give-initial-compass: true
  give-end-crystal: true
  end-crystal-glow-seconds: 15
  hide-name-tags: true
  disable-advancement-announcements: true
  restrict-block-modification-outside-border: true
```

### コンパスとロケーターバー

- `show-player-locator-bar`: Minecraft 標準のプレイヤーロケーターバーを表示するか
- `enable-compass-tracking`: 配布コンパスの追跡処理を有効にするか
- `player-locator-max-alive-players`: 生存者がこの人数以下になったらコンパス追跡を有効化

例: ロケーターバーは非表示、終盤のコンパス追跡だけ有効にする場合

```yaml
show-player-locator-bar: false
enable-compass-tracking: true
player-locator-max-alive-players: 4
```

## ボーダー設定

```yaml
world:
  name: world
  random-center-range: 3000
  initial-border-size: 1000

spawn:
  min-distance: 50

border:
  warning-distance: 10
  warning-time: 5
  final-phase:
    enable-smooth-move: true
    move-range: 40
    move-duration-seconds: 20
```

- `world.name`: 試合に使うワールド名
- `random-center-range`: 初期ボーダー中心をランダムに選ぶ範囲
- `initial-border-size`: 初期ボーダーサイズ
- `spawn.min-distance`: プレイヤー同士の最低スポーン距離
- `move-range`: 最終フェーズで中心が揺れる最大距離
- `move-duration-seconds`: 最終フェーズの最初の移動時間

最終フェーズの移動時間は、1回ごとに約90%へ短縮され、最終的に5秒間隔まで速くなります。

## インストール

1. `build/libs/mineroyale-*.jar` を Paper サーバの `plugins` フォルダへ配置します。
2. サーバを起動します。
3. 生成された `plugins/mineroyale/config.yml` を必要に応じて編集します。
4. `world.name` が実際のワールド名と一致しているか確認します。
5. `/mr start` で試合を開始します。

`world.name` に一致するワールドが存在しない場合、プラグインは理由をログに出して安全に無効化されます。

## ビルドとテスト

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat shadowJar
```

Linux / GitHub Actions:

```bash
./gradlew test
./gradlew shadowJar
```

ビルド済み jar は `build/libs/` に出力されます。
