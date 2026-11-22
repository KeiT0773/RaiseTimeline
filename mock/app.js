/*
 * RaiseTimeline 静的モック
 *
 * HTML / CSS / JavaScript だけで、要件定義書の MVP 機能（F-01〜F-23）と
 * 画面（SC-01〜SC-07）を再現する。サーバー・DB は使わず、データは
 * localStorage に保存する。画面の URL はハッシュ（#/posts/1 など）で表す。
 *
 * 表示する文字列はすべて textContent で入れる（innerHTML は使わない）ので、
 * 本文に <script> などを書いても文字として表示される（XSS 対策の再現）。
 */
(function () {
  "use strict";

  // =========================================================================
  // 定数
  // =========================================================================

  const STORAGE_KEY = "raiseTimeline.mock.v1";
  const SESSION_KEY = "raiseTimeline.mock.session";
  const PAGE_SIZE = 20;
  const POST_MAX = 280;
  const COMMENT_MAX = 280;
  const DISPLAY_NAME_MAX = 30;
  const BIO_MAX = 160;
  const SEARCH_MAX = 50;
  const IMAGE_MAX_COUNT = 4;
  const AVATAR_PX = 400; // アイコンは正方形に切り抜いてこの大きさに縮小する
  const SCHEMA_VERSION = 2; // 2 = アイコン（users[].avatar）を追加
  const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
  const IMAGE_TYPES = ["image/jpeg", "image/png", "image/gif", "image/webp"];
  const RECOMMEND_MAX = 10;

  // =========================================================================
  // 小さな道具
  // =========================================================================

  /** 要素を作る。文字列の子は textContent として入るのでエスケープ不要。 */
  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    if (props) {
      for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key.startsWith("on") && typeof value === "function") {
          el.addEventListener(key.slice(2).toLowerCase(), value);
        } else if (key === "class") {
          el.className = value;
        } else if (["value", "disabled", "checked", "hidden", "maxLength"].includes(key)) {
          el[key] = value;
        } else {
          el.setAttribute(key, value === true ? "" : String(value));
        }
      }
    }
    appendChildren(el, children);
    return el;
  }

  function appendChildren(el, children) {
    for (const child of children.flat(Infinity)) {
      if (child === null || child === undefined || child === false) continue;
      el.appendChild(child instanceof Node ? child : document.createTextNode(String(child)));
    }
  }

  /** 文字数（Unicode のコードポイント単位。post.md 5.） */
  function len(str) {
    return Array.from(str).length;
  }

  const jstParts = new Intl.DateTimeFormat("ja-JP", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });

  function parts(ts) {
    const p = {};
    for (const { type, value } of jstParts.formatToParts(new Date(ts))) p[type] = value;
    return p;
  }

  /** 絶対表示 YYYY/MM/DD HH:mm（投稿詳細） */
  function absTime(ts) {
    const p = parts(ts);
    return `${p.year}/${p.month}/${p.day} ${p.hour}:${p.minute}`;
  }

  /** 相対表示（一覧・コメント）。1 日以上前は日付 */
  function relTime(ts) {
    const sec = (Date.now() - ts) / 1000;
    if (sec < 60) return "たった今";
    if (sec < 3600) return `${Math.floor(sec / 60)}分前`;
    if (sec < 86400) return `${Math.floor(sec / 3600)}時間前`;
    const p = parts(ts);
    return `${p.year}/${p.month}/${p.day}`;
  }

  function sinceText(ts) {
    const p = parts(ts);
    return `${Number(p.year)}年${Number(p.month)}月から利用しています`;
  }

  // =========================================================================
  // 初期データ（ダミー）
  // =========================================================================

  /** 色とラベルから SVG のダミー画像（data URL）を作る */
  function dummyImage(label, hue, w, h) {
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
      `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
      `<stop offset="0" stop-color="hsl(${hue},70%,62%)"/>` +
      `<stop offset="1" stop-color="hsl(${(hue + 60) % 360},70%,45%)"/>` +
      `</linearGradient></defs>` +
      `<rect width="100%" height="100%" fill="url(#g)"/>` +
      `<circle cx="${w * 0.78}" cy="${h * 0.28}" r="${Math.min(w, h) * 0.12}" fill="rgba(255,255,255,0.35)"/>` +
      `<path d="M0 ${h * 0.8} L${w * 0.35} ${h * 0.45} L${w * 0.6} ${h * 0.7} L${w * 0.8} ${h * 0.55} L${w} ${h * 0.75} L${w} ${h} L0 ${h} Z" fill="rgba(0,0,0,0.18)"/>` +
      `<text x="50%" y="52%" font-family="sans-serif" font-size="${Math.round(Math.min(w, h) / 8)}" ` +
      `fill="#fff" text-anchor="middle" dominant-baseline="middle">${label}</text>` +
      `<text x="50%" y="66%" font-family="sans-serif" font-size="${Math.round(Math.min(w, h) / 18)}" ` +
      `fill="rgba(255,255,255,0.85)" text-anchor="middle">${w}×${h}</text>` +
      `</svg>`;
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  }

  function createSeed() {
    const now = Date.now();
    const DAY = 86400000;
    const MIN = 60000;

    const userDefs = [
      ["taro", "山田太郎", "プログラミング学習中です。\nJava と React を勉強しています。", 23],
      ["hanako", "佐藤花子", "Rails 勉強中です。毎日 1 投稿が目標！", 60],
      ["ichiro", "鈴木一郎", "", 55],
      ["han", "半田一郎", "東京でエンジニアをしています。休日はカメラを持って散歩しています。写真の投稿が多めです。", 50],
      ["ihan", "林 一平", "", 40],
      ["Hana_dev", "花田 開発", "フロントエンドが好きです。CSS の小ネタをつぶやきます。", 35],
      ["jiro", "田中次郎", "SQL とデータベースの勉強中", 30],
      ["sakura", "高橋さくら", "デザイン ✕ コード。Figma とにらめっこする毎日。", 28],
      ["kenta", "伊藤健太", "", 20],
      ["yui", "渡辺結衣", "読書と散歩。たまにアプリ開発。", 15],
      ["sora", "中村空", "インフラ勉強中。AWS の資格を取りたい", 10],
      ["mika", "小林美香", "", 3],
    ];
    const users = userDefs.map(([username, displayName, bio, daysAgo], i) => ({
      id: i + 1,
      username,
      displayName,
      email: `${username.toLowerCase()}@example.com`,
      password: "password123",
      bio,
      createdAt: now - daysAgo * DAY,
    }));
    applySeedAvatars(users);
    const uid = (name) => users.find((u) => u.username === name).id;

    // フォロー関係（taro = デモ用アカウント）
    const followPairs = [
      ["taro", "hanako"], ["taro", "ichiro"], ["taro", "sakura"],
      ["hanako", "taro"], ["hanako", "ichiro"], ["hanako", "han"], ["hanako", "sakura"],
      ["ichiro", "hanako"], ["ichiro", "taro"], ["ichiro", "han"],
      ["han", "hanako"], ["han", "Hana_dev"],
      ["ihan", "hanako"], ["ihan", "han"],
      ["Hana_dev", "hanako"], ["Hana_dev", "sakura"], ["Hana_dev", "han"],
      ["jiro", "hanako"], ["jiro", "ichiro"], ["jiro", "han"],
      ["sakura", "hanako"], ["sakura", "Hana_dev"],
      ["kenta", "hanako"], ["kenta", "han"], ["kenta", "jiro"],
      ["yui", "hanako"], ["yui", "sakura"],
      ["sora", "hanako"], ["sora", "jiro"], ["sora", "han"],
      ["mika", "hanako"], ["mika", "ichiro"],
    ];
    const follows = followPairs.map(([a, b]) => ({ followerId: uid(a), followedId: uid(b) }));

    // 投稿（古い順に並べ、id が大きいほど新しい）
    const postDefs = [
      ["mika", "はじめまして。今日から使い始めました！", 0],
      ["sora", "VPC とサブネットの関係がやっと分かってきた。パブリックとプライベートの違い、図に描くと理解しやすい。", 0],
      ["yui", "朝の散歩。空がきれいでした。", 2],
      ["kenta", "腕立て 30 回、継続 10 日目。", 0],
      ["sakura", "配色を考えるときは、まず背景色と文字色のコントラストから決めるようにしています。", 0],
      ["jiro", "JOIN の種類、INNER と LEFT の違いを図で整理した。NULL が出てくるのが LEFT JOIN。", 0],
      ["Hana_dev", "CSS Grid の grid-template-areas、レイアウトが一目で分かって好き。", 0],
      ["ihan", "週末はキャンプに行ってきます⛺", 0],
      ["han", "", 3],
      ["ichiro", "resources を使うとルーティングが一気に短くなる。", 0],
      ["hanako", "今日は Rails のルーティングを勉強しました！", 2],
      ["taro", "初投稿です。よろしくお願いします。", 0],
      ["sora", "セキュリティグループは「許可」だけを書くのがポイントらしい。", 0],
      ["yui", "読み終わった本。付箋だらけになった。", 1],
      ["kenta", "プログラミング始めました。まずは変数から。", 0],
      ["sakura", "余白を倍にしたら、急に垢抜けた。余白は正義。", 4],
      ["jiro", "インデックスを貼ったら検索が 100 倍速くなった。感動。", 0],
      ["Hana_dev", "aspect-ratio プロパティ、画像のレイアウトでとても便利。", 1],
      ["hanako", "コントローラーとモデルの役割の違いがやっと腑に落ちた気がする。", 0],
      ["han", "夕焼けを撮りに行ってきました。", 2],
      ["ichiro", "テストを書く習慣をつけたい。", 0],
      ["taro", "Spring Boot の DI、最初は魔法に見えたけど仕組みが分かってきた。", 0],
      ["mika", "おすすめのユーザーさんをフォローしてみました。", 0],
      ["sora", "Terraform で EC2 を 1 台立てるところまでできた！", 1],
      ["yui", "カフェで作業。はかどる。", 0],
      ["hanako", "N+1 問題、名前は知っていたけど実際に SQL のログを見てびっくり。", 0],
      ["jiro", "外部キー制約で ON DELETE CASCADE を付けるかどうか、毎回悩む。", 0],
      ["sakura", "ボタンの角丸は 8px か 999px か問題。", 0],
      ["Hana_dev", "", 4],
      ["han", "東京駅の丸の内側、夜景がきれい。", 1],
      ["ichiro", "今日は 1 時間だけ勉強。ゼロよりはずっといい。", 0],
      ["taro", "React の useEffect、依存配列を書き忘れて無限ループした…", 0],
      ["kenta", "for 文が書けるようになった！", 0],
      ["hanako", "毎日 1 投稿、30 日続きました🎉", 3],
      ["sora", "IAM ロールとユーザーの違い、ようやく理解。", 0],
      ["yui", "新しいノートを買いました。", 1],
      ["jiro", "EXPLAIN ANALYZE を読めるようになりたい。", 0],
      ["sakura", "フォントを変えるだけで印象がガラッと変わる。", 0],
      ["ichiro", "Git のブランチ運用、Issue → ブランチ → PR の流れに慣れてきた。", 0],
      ["han", "雨の日の写真も好きです。", 1],
      ["taro", "画像を 2 枚付けて投稿してみるテスト。", 2],
      ["Hana_dev", "モバイル幅で横スクロールが出たら、まず width: 100vw を疑う。", 0],
      ["hanako", "コメントくれた皆さんありがとうございます！", 0],
      ["mika", "まだ使い方がよく分からないけど、楽しい。", 0],
      ["sakura", "今日のスケッチ。", 1],
      ["ichiro", "<script>alert(1)</script> ← こういう文字を書いても、ちゃんと文字として表示されるか確認。", 0],
      ["hanako", "週末にもくもく会をやります。参加したい人いますか？", 0],
      ["taro", "RaiseTimeline のモックができました。いろいろ触ってみてください！", 0],
    ];
    const sizes = [[1200, 675], [800, 800], [900, 1200], [1200, 800]];
    const posts = postDefs.map(([username, content, imageCount], i) => {
      const id = i + 1;
      const images = [];
      for (let n = 0; n < imageCount; n++) {
        const [w, hgt] = sizes[(id + n) % sizes.length];
        images.push(dummyImage(`画像 ${n + 1}`, (id * 47 + n * 70) % 360, w, hgt));
      }
      // 新しいものほど現在に近い。最新は 3 分前、そこから約 1〜2 時間ずつ古くなる
      const minutesAgo = 3 + (postDefs.length - 1 - i) * 97;
      return { id, userId: uid(username), content, images, createdAt: now - minutesAgo * MIN };
    });

    // いいね（簡単な擬似乱数で決める）
    let seed = 7;
    const rand = () => {
      seed = (seed * 9301 + 49297) % 233280;
      return seed / 233280;
    };
    const likes = [];
    for (const post of posts) {
      for (const user of users) {
        const popular = ["hanako", "han", "sakura"].includes(users[post.userId - 1].username);
        if (rand() < (popular ? 0.45 : 0.2)) likes.push({ userId: user.id, postId: post.id });
      }
    }

    // コメント
    const commentDefs = [
      [11, "taro", "僕も今ちょうど同じところをやってます！", 9],
      [11, "ichiro", "resources 便利ですよね", 6],
      [12, "hanako", "よろしくお願いします！", 50],
      [26, "jiro", "includes で解決できますよ", 30],
      [26, "taro", "ログを見るの大事ですね", 20],
      [34, "taro", "すごい！おめでとうございます", 15],
      [34, "sakura", "継続力すごい…！", 12],
      [34, "yui", "おめでとうございます🎉", 8],
      [41, "hanako", "画像きれいに並んでますね", 5],
      [47, "taro", "参加したいです！", 2],
      [48, "hanako", "触ってみます！", 1],
    ];
    const comments = commentDefs.map(([postId, username, content, minutesAfter], i) => ({
      id: i + 1,
      postId,
      userId: uid(username),
      content,
      createdAt: Math.min(posts[postId - 1].createdAt + minutesAfter * MIN, now - 30000),
    }));

    return {
      users,
      posts,
      comments,
      likes,
      follows,
      nextId: { user: users.length + 1, post: posts.length + 1, comment: comments.length + 1 },
      schema: SCHEMA_VERSION,
    };
  }

  /** ダミーのアイコン画像（人のシルエット）を作る */
  function dummyAvatar(hue) {
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400">` +
      `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
      `<stop offset="0" stop-color="hsl(${hue},75%,70%)"/><stop offset="1" stop-color="hsl(${(hue + 40) % 360},70%,48%)"/>` +
      `</linearGradient></defs>` +
      `<rect width="400" height="400" fill="url(#g)"/>` +
      `<circle cx="200" cy="160" r="72" fill="rgba(255,255,255,0.9)"/>` +
      `<ellipse cx="200" cy="370" rx="130" ry="110" fill="rgba(255,255,255,0.9)"/>` +
      `</svg>`;
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  }

  /** 初期データのうち、アイコンを設定済みにしておくユーザー（残りは頭文字アイコン） */
  const SEED_AVATARS = { hanako: 330, han: 200, Hana_dev: 270, sakura: 350, jiro: 30, sora: 180 };

  function applySeedAvatars(users) {
    for (const user of users) {
      if (user.avatar === undefined) user.avatar = null;
      if (SEED_AVATARS[user.username] !== undefined && !user.avatar) user.avatar = dummyAvatar(SEED_AVATARS[user.username]);
    }
  }

  // =========================================================================
  // データの保存・読み込み（localStorage）
  // =========================================================================

  let db = loadDb();
  let session = loadSession();

  function loadDb() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const data = JSON.parse(raw);
        if ((data.schema || 1) < SCHEMA_VERSION) {
          // 版 1（アイコンなし）で保存されたデータに、初期データのアイコンを足す
          applySeedAvatars(data.users);
          data.schema = SCHEMA_VERSION;
          localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        }
        return data;
      }
    } catch (e) {
      /* 読めないときは初期データで動かす */
    }
    return createSeed();
  }

  function saveDb() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch (e) {
      flash("ブラウザの保存容量が足りません。この変更は再読み込みすると消えます。", "error");
    }
  }

  function loadSession() {
    try {
      const id = Number(localStorage.getItem(SESSION_KEY));
      return id || null;
    } catch (e) {
      return null;
    }
  }

  function setSession(userId) {
    session = userId;
    try {
      if (userId) localStorage.setItem(SESSION_KEY, String(userId));
      else localStorage.removeItem(SESSION_KEY);
    } catch (e) {
      /* メモリ上だけで保持する */
    }
  }

  // =========================================================================
  // データ操作（本来はバックエンドの API が行う処理）
  // =========================================================================

  const userById = (id) => db.users.find((u) => u.id === id);
  const userByName = (name) => db.users.find((u) => u.username.toLowerCase() === String(name).toLowerCase());
  const postById = (id) => db.posts.find((p) => p.id === id);
  const me = () => (session ? userById(session) : null);

  const likeCount = (postId) => db.likes.filter((l) => l.postId === postId).length;
  const commentCount = (postId) => db.comments.filter((c) => c.postId === postId).length;
  const isLiked = (postId) => db.likes.some((l) => l.postId === postId && l.userId === session);
  const isFollowing = (a, b) => db.follows.some((f) => f.followerId === a && f.followedId === b);
  const followingCount = (id) => db.follows.filter((f) => f.followerId === id).length;
  const followerCount = (id) => db.follows.filter((f) => f.followedId === id).length;
  const postCount = (id) => db.posts.filter((p) => p.userId === id).length;

  function toggleLike(postId) {
    if (isLiked(postId)) {
      db.likes = db.likes.filter((l) => !(l.postId === postId && l.userId === session));
    } else {
      db.likes.push({ userId: session, postId });
    }
    saveDb();
  }

  function toggleFollow(targetId) {
    if (targetId === session) return; // 自分自身はフォローできない
    if (isFollowing(session, targetId)) {
      db.follows = db.follows.filter((f) => !(f.followerId === session && f.followedId === targetId));
    } else {
      db.follows.push({ followerId: session, followedId: targetId });
    }
    saveDb();
  }

  function deletePost(postId) {
    const post = postById(postId);
    if (!post || post.userId !== session) return false; // 本人以外は削除できない
    db.posts = db.posts.filter((p) => p.id !== postId);
    db.comments = db.comments.filter((c) => c.postId !== postId);
    db.likes = db.likes.filter((l) => l.postId !== postId);
    saveDb();
    return true;
  }

  /** 投稿をページ単位で取る（id の降順。cursor より古いものだけ） */
  function fetchPosts(filter, cursor) {
    const all = db.posts
      .filter(filter)
      .filter((p) => cursor === null || p.id < cursor)
      .sort((a, b) => b.id - a.id);
    const items = all.slice(0, PAGE_SIZE);
    const next = all.length > PAGE_SIZE ? items[items.length - 1].id : null;
    return { items, next };
  }

  // =========================================================================
  // ルーター（ハッシュで画面の URL を表す）
  // =========================================================================

  const app = document.getElementById("app");
  const header = document.getElementById("site-header");
  let navCount = 0; // アプリ内で何回画面を移ったか（← 戻る の判定に使う）

  function parseHash() {
    const raw = location.hash.replace(/^#/, "") || "/";
    const [path, query = ""] = raw.split("?");
    return { path, params: new URLSearchParams(query) };
  }

  function go(hash) {
    if (location.hash === hash) render();
    else location.hash = hash;
  }

  const routes = [
    { re: /^\/login$/, auth: false, view: viewLogin },
    { re: /^\/signup$/, auth: false, view: viewSignup },
    { re: /^\/$/, auth: true, view: viewTimeline },
    { re: /^\/posts\/(\d+)$/, auth: true, view: viewPostDetail },
    { re: /^\/users\/([^/]+)$/, auth: true, view: viewProfile },
    { re: /^\/settings\/profile$/, auth: true, view: viewProfileEdit },
    { re: /^\/search$/, auth: true, view: viewSearch },
  ];

  function render() {
    const { path, params } = parseHash();
    const route = routes.find((r) => r.re.test(path));

    // F-04 アクセス制御
    if (session && !me()) setSession(null);
    if (route && route.auth && !session) return go("#/login");
    if (route && !route.auth && session) return go("#/");

    closeViewer();
    renderHeader();
    app.replaceChildren();
    window.scrollTo(0, 0);

    if (!route) {
      app.append(h("div", { class: "empty" }, "ページが見つかりません。", h("br"), h("a", { href: "#/" }, "タイムラインへ")));
      return;
    }
    const match = path.match(route.re).slice(1).map(decodeURIComponent);
    route.view(params, ...match);
  }

  window.addEventListener("hashchange", () => {
    navCount++;
    render();
  });

  // =========================================================================
  // フラッシュメッセージ
  // =========================================================================

  const flashEl = document.getElementById("flash");
  let flashTimer = null;

  function flash(message, type) {
    flashEl.textContent = message;
    flashEl.className = "flash" + (type === "error" ? " error" : "");
    flashEl.hidden = false;
    clearTimeout(flashTimer);
    flashTimer = setTimeout(() => (flashEl.hidden = true), 3000);
  }

  // =========================================================================
  // 共通部品
  // =========================================================================

  function renderHeader() {
    const user = me();
    header.hidden = !user;
    header.replaceChildren();
    if (!user) return;
    header.append(
      h(
        "div",
        { class: "header-inner" },
        h("a", { class: "logo", href: "#/" }, "RaiseTimeline"),
        h("a", { class: "header-search", href: "#/search" }, "🔍 ユーザーを探す"),
        h(
          "div",
          { class: "header-me" },
          h(
            "a",
            { class: "header-me-link", href: `#/users/${encodeURIComponent(user.username)}`, title: "自分のプロフィール" },
            avatar(user, 28),
            h("span", { class: "name" }, user.displayName),
            " ",
            h("span", { class: "handle muted" }, "@" + user.username)
          ),
          h(
            "button",
            {
              type: "button",
              class: "btn btn-outline",
              onClick: () => {
                // F-03 ログアウト（確認なし）
                setSession(null);
                go("#/login");
              },
            },
            "ログアウト"
          )
        )
      )
    );
  }

  /** 頭文字アイコンの背景色。ユーザー ID から決めるので、表示名を変えても変わらない */
  function avatarColor(userId) {
    return `hsl(${(userId * 137) % 360}, 55%, 45%)`;
  }

  /**
   * アイコン（丸）。画像が無ければ表示名の頭文字を表示する。
   * src を渡すとその画像で表示する（プロフィール編集のプレビュー用。null なら頭文字）。
   */
  function avatar(user, size, src) {
    const image = src === undefined ? user.avatar : src;
    const style = `width:${size}px;height:${size}px;font-size:${Math.round(size * 0.45)}px;background:${image ? "transparent" : avatarColor(user.id)}`;
    if (image) {
      return h("img", { class: "avatar", src: image, alt: "", style, loading: "lazy" });
    }
    const initial = Array.from(user.displayName.trim())[0] || "?";
    return h("span", { class: "avatar avatar-initial", style, "aria-hidden": "true" }, initial.toUpperCase());
  }

  /** アイコンのリンク（クリックでプロフィールへ。名前のリンクと同じ行き先なのでキーボード操作の対象からは外す） */
  function avatarLink(user, size) {
    return h(
      "a",
      {
        class: "avatar-link",
        href: `#/users/${encodeURIComponent(user.username)}`,
        tabindex: "-1",
        "aria-hidden": "true",
        onClick: (e) => e.stopPropagation(),
      },
      avatar(user, size)
    );
  }

  /** 表示名・@ユーザー名のリンク（名前の文字の部分だけが反応する） */
  function userLink(user) {
    return h(
      "a",
      { class: "user-link", href: `#/users/${encodeURIComponent(user.username)}` },
      h("span", { class: "name" }, user.displayName),
      " ",
      h("span", { class: "handle" }, "@" + user.username)
    );
  }

  /** 残り文字数の表示 */
  function counter(max) {
    const el = h("span", { class: "counter" });
    el.update = (count) => {
      el.textContent = `残り ${max - count}`;
      el.classList.toggle("over", count > max);
    };
    el.update(0);
    return el;
  }

  /** フォローボタン（F-19）。自分自身には出さない */
  function followButton(target, onChange) {
    const btn = h("button", { type: "button", class: "btn follow-btn" });
    const paint = () => {
      const following = isFollowing(session, target.id);
      btn.classList.toggle("is-following", following);
      btn.classList.toggle("btn-outline", following);
      btn.classList.toggle("btn-dark", !following);
      btn.setAttribute("aria-pressed", String(following));
      btn.replaceChildren(
        ...(following
          ? [h("span", { class: "when-normal" }, "フォロー中"), h("span", { class: "when-hover" }, "フォロー解除")]
          : ["フォローする"])
      );
    };
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      toggleFollow(target.id);
      paint();
      if (onChange) onChange();
    });
    paint();
    return btn;
  }

  /** 「さらに読み込む」付きの一覧。fetch(cursor) は { items, next } を返す */
  function pagedList(fetch, renderItem, emptyNode) {
    const wrap = h("div");
    const list = h("div");
    const moreWrap = h("div", { class: "more" });
    const moreBtn = h("button", { type: "button", class: "btn btn-outline" }, "さらに読み込む");
    moreWrap.append(moreBtn);
    wrap.append(list, moreWrap);

    let cursor = null;
    const load = () => {
      const { items, next } = fetch(cursor);
      for (const item of items) list.append(renderItem(item));
      cursor = next;
      moreWrap.hidden = next === null;
      if (list.childElementCount === 0 && emptyNode) list.append(emptyNode);
    };
    moreBtn.addEventListener("click", load);
    load();
    return wrap;
  }

  /** 添付画像の表示（一覧は切り抜き、詳細は元の縦横比） */
  function mediaGrid(post, full) {
    const n = post.images.length;
    if (n === 0) return null;
    const author = userById(post.userId);
    const grid = h("div", { class: `media n${n}` + (full ? " full" + (n > 1 ? " multi" : "") : "") });
    post.images.forEach((src, i) => {
      grid.append(
        h("img", {
          src,
          loading: "lazy",
          alt: `${author.displayName}さんの投稿画像 ${i + 1}/${n}`,
          tabindex: "0",
          onClick: (e) => {
            e.stopPropagation();
            openViewer(post, i);
          },
          onKeydown: (e) => {
            if (e.key === "Enter") {
              e.stopPropagation();
              openViewer(post, i);
            }
          },
        })
      );
    });
    return grid;
  }

  /** いいねボタン（F-14 / F-15） */
  function likeButton(post) {
    const btn = h("button", { type: "button", class: "action like-btn" });
    const paint = () => {
      const liked = isLiked(post.id);
      btn.classList.toggle("liked", liked);
      btn.setAttribute("aria-pressed", String(liked));
      btn.setAttribute("aria-label", liked ? "いいねを取り消す" : "いいね");
      btn.replaceChildren(h("span", { class: "icon" }, liked ? "♥" : "♡"), String(likeCount(post.id)));
    };
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      if (!postById(post.id)) return flash("この投稿は削除されました。", "error");
      toggleLike(post.id);
      paint();
    });
    paint();
    return btn;
  }

  /** 投稿カード。detail=true なら投稿詳細（SC-04）の表示 */
  function postCard(post, opts) {
    const detail = !!(opts && opts.detail);
    const author = userById(post.userId);
    const mine = post.userId === session;

    const commentLink = h(
      "a",
      {
        class: "action",
        href: `#/posts/${post.id}?focus=comments`,
        "aria-label": "コメント",
        onClick: (e) => e.stopPropagation(),
      },
      h("span", { class: "icon" }, "💬"),
      h("span", { "data-comment-count": post.id }, String(commentCount(post.id)))
    );

    let card; // 削除ボタンから参照する
    const contents = [
      h(
        "div",
        { class: "post-head" },
        detail ? avatarLink(author, 48) : null,
        userLink(author),
        detail ? null : h("span", { class: "post-time" }, "・ " + relTime(post.createdAt)),
        h("span", { class: "spacer" }),
        mine
          ? h(
              "button",
              {
                type: "button",
                class: "btn btn-danger-text",
                onClick: (e) => {
                  e.stopPropagation();
                  // F-10 投稿削除（確認ダイアログあり）
                  if (!confirm("この投稿を削除しますか？")) return;
                  if (!deletePost(post.id)) return flash("この投稿は既に削除されています。", "error");
                  flash("投稿を削除しました");
                  if (detail) return go("#/");
                  card.remove();
                  if (opts && opts.onDelete) opts.onDelete();
                },
              },
              "削除"
            )
          : null
      ),
      post.content ? h("p", { class: "post-body" }, post.content) : null,
      mediaGrid(post, detail),
      detail ? h("div", { class: "post-abs-time" }, absTime(post.createdAt)) : null,
      h("div", { class: "post-actions" }, commentLink, likeButton(post)),
    ];

    // 一覧ではアイコンを左の列に、詳細では名前の左に置く
    card = detail
      ? h("article", { class: "post detail" }, contents)
      : h("article", { class: "post with-avatar" }, avatarLink(author, 40), h("div", { class: "post-main" }, contents));

    if (!detail) {
      // 本文部分のクリックで投稿詳細へ（名前・ボタン・画像は除く）
      card.addEventListener("click", (e) => {
        if (e.target.closest("a, button, img")) return;
        if (window.getSelection().toString()) return; // 文字の選択中は移動しない
        go(`#/posts/${post.id}`);
      });
    }
    return card;
  }

  // =========================================================================
  // 画像ビューアー（モーダル）
  // =========================================================================

  const viewer = document.getElementById("viewer");
  const viewerImg = viewer.querySelector(".viewer-img");
  const viewerPrev = viewer.querySelector(".viewer-prev");
  const viewerNext = viewer.querySelector(".viewer-next");
  const viewerCount = viewer.querySelector(".viewer-count");
  let viewerState = null;
  let viewerReturnFocus = null;

  function openViewer(post, index) {
    viewerReturnFocus = document.activeElement;
    viewerState = { post, index };
    viewer.hidden = false;
    document.body.style.overflow = "hidden";
    paintViewer();
    viewer.querySelector(".viewer-close").focus();
  }

  function paintViewer() {
    const { post, index } = viewerState;
    const n = post.images.length;
    const author = userById(post.userId);
    viewerImg.src = post.images[index];
    viewerImg.alt = `${author.displayName}さんの投稿画像 ${index + 1}/${n}`;
    viewerPrev.hidden = index === 0;
    viewerNext.hidden = index === n - 1;
    viewerCount.textContent = n > 1 ? `${index + 1} / ${n}` : "";
  }

  function moveViewer(delta) {
    if (!viewerState) return;
    const next = viewerState.index + delta;
    if (next < 0 || next >= viewerState.post.images.length) return;
    viewerState.index = next;
    paintViewer();
  }

  function closeViewer() {
    if (!viewerState) return;
    viewerState = null;
    viewer.hidden = true;
    document.body.style.overflow = "";
    if (viewerReturnFocus && document.contains(viewerReturnFocus)) viewerReturnFocus.focus();
  }

  viewer.addEventListener("click", (e) => {
    if (e.target === viewer) closeViewer(); // 背景クリック
  });
  viewer.querySelector(".viewer-close").addEventListener("click", closeViewer);
  viewerPrev.addEventListener("click", () => moveViewer(-1));
  viewerNext.addEventListener("click", () => moveViewer(1));
  document.addEventListener("keydown", (e) => {
    if (!viewerState) return;
    if (e.key === "Escape") closeViewer();
    else if (e.key === "ArrowLeft") moveViewer(-1);
    else if (e.key === "ArrowRight") moveViewer(1);
  });

  // =========================================================================
  // SC-01 ログイン（F-02）
  // =========================================================================

  function viewLogin() {
    const email = h("input", { class: "input", type: "email", id: "login-email", required: true, autocomplete: "email" });
    const password = h("input", {
      class: "input",
      type: "password",
      id: "login-password",
      required: true,
      autocomplete: "current-password",
    });
    const error = h("div", { class: "form-error-box", role: "alert", hidden: true });

    const form = h(
      "form",
      {
        class: "auth",
        novalidate: true,
        onSubmit: (e) => {
          e.preventDefault();
          const user = db.users.find(
            (u) => u.email.toLowerCase() === email.value.trim().toLowerCase() && u.password === password.value
          );
          if (!user) {
            // どちらが違うかは伝えない
            error.textContent = "メールアドレスまたはパスワードが正しくありません";
            error.hidden = false;
            password.value = "";
            return;
          }
          setSession(user.id);
          go("#/");
        },
      },
      h("span", { class: "logo" }, "RaiseTimeline"),
      h("h1", null, "ログイン"),
      error,
      h("div", { class: "field" }, h("label", { for: "login-email" }, "メールアドレス"), email),
      h("div", { class: "field" }, h("label", { for: "login-password" }, "パスワード"), password),
      h("button", { type: "submit", class: "btn btn-primary btn-block" }, "ログイン"),
      h("p", { class: "auth-switch" }, "アカウントをお持ちでない方は ", h("a", { href: "#/signup" }, "新規登録")),
      h(
        "div",
        { class: "demo-box" },
        "デモ用アカウント：",
        h("br"),
        "メール ",
        h("code", null, "taro@example.com"),
        " ／ パスワード ",
        h("code", null, "password123"),
        h("br"),
        "（ほかのユーザーも「ユーザー名@example.com」と同じパスワードでログインできます）",
        h("br"),
        h(
          "button",
          {
            type: "button",
            class: "btn btn-link",
            onClick: () => {
              email.value = "taro@example.com";
              password.value = "password123";
            },
          },
          "デモ用アカウントを入力する"
        )
      )
    );
    app.append(form);
    email.focus();
  }

  // =========================================================================
  // SC-02 新規登録（F-01）
  // =========================================================================

  function viewSignup() {
    const fields = {};
    const field = (key, label, input, prefix) => {
      const err = h("p", { class: "error", id: `err-${key}` });
      input.id = `signup-${key}`;
      input.setAttribute("aria-describedby", `err-${key}`);
      fields[key] = { input, err };
      return h(
        "div",
        { class: "field" },
        h("label", { for: input.id }, label),
        prefix ? h("div", { class: "input-prefix" }, h("span", { class: "muted" }, "@"), input) : input,
        err
      );
    };

    const form = h(
      "form",
      {
        class: "auth",
        novalidate: true,
        onSubmit: (e) => {
          e.preventDefault();
          const v = (k) => fields[k].input.value;
          const errors = {};
          const username = v("username").trim();
          const displayName = v("displayName").trim();
          const email = v("email").trim();

          if (!/^[A-Za-z0-9_]{3,15}$/.test(username)) {
            errors.username = "ユーザー名は半角英数字と _ で 3〜15 文字にしてください";
          } else if (userByName(username)) {
            errors.username = "このユーザー名は既に使われています";
          }
          if (len(displayName) === 0) errors.displayName = "表示名を入力してください";
          else if (len(displayName) > DISPLAY_NAME_MAX) errors.displayName = "表示名は 30 文字以内にしてください";
          if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
            errors.email = "メールアドレスの形式が正しくありません";
          } else if (db.users.some((u) => u.email.toLowerCase() === email.toLowerCase())) {
            errors.email = "このメールアドレスは既に登録されています";
          }
          if (len(v("password")) < 8) errors.password = "パスワードは 8 文字以上にしてください";
          else if (len(v("password")) > 72) errors.password = "パスワードは 72 文字以内にしてください";
          if (v("password") !== v("passwordConfirm")) errors.passwordConfirm = "パスワードが一致しません";

          for (const [k, f] of Object.entries(fields)) {
            f.err.textContent = errors[k] || "";
            f.input.setAttribute("aria-invalid", String(!!errors[k]));
          }
          if (Object.keys(errors).length) {
            fields[Object.keys(errors)[0]].input.focus();
            return;
          }

          const user = {
            id: db.nextId.user++,
            username,
            displayName,
            email,
            password: v("password"),
            bio: "",
            avatar: null,
            createdAt: Date.now(),
          };
          db.users.push(user);
          saveDb();
          setSession(user.id); // 登録後はそのままログイン状態
          flash("アカウントを作成しました");
          go("#/");
        },
      },
      h("span", { class: "logo" }, "RaiseTimeline"),
      h("h1", null, "アカウントを作成"),
      field("username", "ユーザー名（半角英数字と _、3〜15 文字。後から変更できません）", h("input", { class: "input", autocomplete: "username", maxLength: 15 }), true),
      field("displayName", "表示名", h("input", { class: "input", autocomplete: "nickname" })),
      field("email", "メールアドレス", h("input", { class: "input", type: "email", autocomplete: "email" })),
      field("password", "パスワード（8 文字以上）", h("input", { class: "input", type: "password", autocomplete: "new-password" })),
      field("passwordConfirm", "パスワード（確認）", h("input", { class: "input", type: "password", autocomplete: "new-password" })),
      h("button", { type: "submit", class: "btn btn-primary btn-block" }, "登録する"),
      h("p", { class: "auth-switch" }, "既にアカウントをお持ちの方は ", h("a", { href: "#/login" }, "ログイン"))
    );
    app.append(form);
    fields.username.input.focus();
  }

  // =========================================================================
  // SC-03 タイムライン（F-05〜F-08, F-10, F-14, F-15, F-17, F-21）
  // =========================================================================

  function viewTimeline(params) {
    const following = params.get("tab") === "following";

    app.append(
      h(
        "nav",
        { class: "tabs", "aria-label": "タイムラインの切り替え" },
        h("a", { class: "tab" + (following ? "" : " active"), href: "#/", "aria-current": following ? null : "page" }, "すべて"),
        h(
          "a",
          { class: "tab" + (following ? " active" : ""), href: "#/?tab=following", "aria-current": following ? "page" : null },
          "フォロー中"
        )
      ),
      composer(() => render())
    );

    const filter = following
      ? (p) => p.userId === session || isFollowing(session, p.userId)
      : () => true;
    const empty = following
      ? h(
          "div",
          { class: "empty" },
          h("p", null, "フォロー中のユーザーの投稿はまだありません。"),
          h("a", { class: "btn btn-primary", href: "#/search" }, "ユーザーを探す")
        )
      : h("div", { class: "empty" }, "まだ投稿がありません");

    app.append(pagedList((cursor) => fetchPosts(filter, cursor), (p) => postCard(p), empty));
  }

  /** 投稿フォーム（F-08 / F-17） */
  function composer(onPosted) {
    const user = me();
    let images = []; // { src } の配列

    const textarea = h("textarea", {
      class: "textarea",
      placeholder: "いまどうしてる？",
      "aria-label": "投稿の本文",
      rows: "3",
    });
    const previews = h("div", { class: "previews" });
    const fileInput = h("input", {
      type: "file",
      accept: IMAGE_TYPES.join(","),
      multiple: true,
      hidden: true,
    });
    const imageBtn = h("button", { type: "button", class: "icon-btn", title: "画像を添付（最大 4 枚）", "aria-label": "画像を添付" }, "🖼️");
    const count = counter(POST_MAX);
    const submit = h("button", { type: "submit", class: "btn btn-primary" }, "投稿");
    const error = h("p", { class: "error", role: "alert" });

    const refresh = () => {
      const n = len(textarea.value.trim());
      count.update(n);
      submit.disabled = n > POST_MAX || (n === 0 && images.length === 0);
      imageBtn.disabled = images.length >= IMAGE_MAX_COUNT;
      previews.replaceChildren(
        ...images.map((img, i) =>
          h(
            "div",
            { class: "preview" },
            h("img", { src: img, alt: `添付画像 ${i + 1}` }),
            h(
              "button",
              {
                type: "button",
                "aria-label": `添付画像 ${i + 1} を取り消す`,
                onClick: () => {
                  images.splice(i, 1);
                  error.textContent = "";
                  refresh();
                },
              },
              "×"
            )
          )
        )
      );
    };

    imageBtn.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", async () => {
      const files = Array.from(fileInput.files);
      fileInput.value = "";
      error.textContent = "";
      for (const file of files) {
        if (images.length >= IMAGE_MAX_COUNT) {
          error.textContent = "画像は 4 枚まで添付できます";
          break;
        }
        if (!IMAGE_TYPES.includes(file.type)) {
          error.textContent = "JPEG・PNG・GIF・WebP の画像を選択してください";
          continue;
        }
        if (file.size > IMAGE_MAX_BYTES) {
          error.textContent = "5MB 以下の画像を選択してください";
          continue;
        }
        try {
          images.push(await shrinkImage(file));
        } catch (e) {
          error.textContent = "JPEG・PNG・GIF・WebP の画像を選択してください";
        }
      }
      refresh();
    });
    textarea.addEventListener("input", refresh);

    const form = h(
      "form",
      {
        class: "section composer",
        onSubmit: (e) => {
          e.preventDefault();
          const content = textarea.value.trim();
          if (len(content) === 0 && images.length === 0) {
            error.textContent = "本文を入力するか、画像を添付してください";
            return;
          }
          if (len(content) > POST_MAX) {
            error.textContent = "本文は 280 文字以内にしてください";
            return;
          }
          db.posts.push({ id: db.nextId.post++, userId: user.id, content, images: images.slice(), createdAt: Date.now() });
          saveDb();
          flash("投稿しました");
          onPosted();
        },
      },
      textarea,
      previews,
      error,
      h("div", { class: "composer-bar" }, imageBtn, fileInput, h("span", { class: "spacer" }), count, submit)
    );
    refresh();
    return form;
  }

  /**
   * 選んだ画像を縮小して data URL にする（localStorage の容量対策）。
   * GIF も 1 フレーム目の静止画になる（要件どおり、アニメは再生しない）。
   */
  function shrinkImage(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = reject;
      reader.onload = () => {
        const img = new Image();
        img.onerror = reject;
        img.onload = () => {
          const scale = Math.min(1, 1200 / Math.max(img.width, img.height));
          const canvas = document.createElement("canvas");
          canvas.width = Math.round(img.width * scale);
          canvas.height = Math.round(img.height * scale);
          const ctx = canvas.getContext("2d");
          ctx.fillStyle = "#fff";
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
          resolve(canvas.toDataURL("image/jpeg", 0.82));
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  // =========================================================================
  // SC-04 投稿詳細（F-09〜F-15, F-17）
  // =========================================================================

  function viewPostDetail(params, idText) {
    const backBar = h(
      "div",
      { class: "back-bar" },
      h(
        "button",
        {
          type: "button",
          class: "btn btn-outline",
          onClick: () => (navCount > 0 ? history.back() : go("#/")),
        },
        "← 戻る"
      )
    );
    app.append(backBar);

    const post = postById(Number(idText));
    if (!post) {
      app.append(h("div", { class: "empty" }, "この投稿は存在しないか、削除されました"));
      return;
    }

    app.append(postCard(post, { detail: true }));

    // コメントフォーム（F-11）
    const textarea = h("textarea", { class: "textarea", placeholder: "コメントを入力", "aria-label": "コメント", rows: "2" });
    const count = counter(COMMENT_MAX);
    const submit = h("button", { type: "submit", class: "btn btn-primary", disabled: true }, "コメント");
    const error = h("p", { class: "error", role: "alert" });
    const list = h("div", { id: "comments" });

    const updateCount = () => {
      for (const el of document.querySelectorAll(`[data-comment-count="${post.id}"]`)) {
        el.textContent = String(commentCount(post.id));
      }
    };

    const renderComments = () => {
      // F-12 古い順
      const items = db.comments.filter((c) => c.postId === post.id).sort((a, b) => a.id - b.id);
      list.replaceChildren(
        ...(items.length ? items.map(commentRow) : [h("div", { class: "empty" }, "まだコメントはありません")])
      );
    };

    const commentRow = (c) => {
      const author = userById(c.userId);
      return h(
        "div",
        { class: "comment with-avatar" },
        avatarLink(author, 32),
        h(
          "div",
          { class: "post-main" },
          commentHead(c, author),
          h("p", { class: "post-body" }, c.content)
        )
      );
    };

    const commentHead = (c, author) =>
        h(
          "div",
          { class: "post-head" },
          userLink(author),
          h("span", { class: "post-time" }, "・ " + relTime(c.createdAt)),
          h("span", { class: "spacer" }),
          c.userId === session
            ? h(
                "button",
                {
                  type: "button",
                  class: "btn btn-danger-text",
                  onClick: () => {
                    // F-13 コメント削除
                    if (!confirm("このコメントを削除しますか？")) return;
                    db.comments = db.comments.filter((x) => x.id !== c.id);
                    saveDb();
                    flash("コメントを削除しました");
                    renderComments();
                    updateCount();
                  },
                },
                "削除"
              )
            : null
        );

    textarea.addEventListener("input", () => {
      const n = len(textarea.value.trim());
      count.update(n);
      submit.disabled = n === 0 || n > COMMENT_MAX;
    });

    const form = h(
      "form",
      {
        class: "section comment-form",
        onSubmit: (e) => {
          e.preventDefault();
          const content = textarea.value.trim();
          if (len(content) === 0) return (error.textContent = "コメントを入力してください");
          if (len(content) > COMMENT_MAX) return (error.textContent = "コメントは 280 文字以内にしてください");
          if (!postById(post.id)) return flash("この投稿は削除されました。", "error");
          db.comments.push({ id: db.nextId.comment++, postId: post.id, userId: session, content, createdAt: Date.now() });
          saveDb();
          textarea.value = "";
          textarea.dispatchEvent(new Event("input"));
          error.textContent = "";
          renderComments();
          updateCount();
          list.lastChild.scrollIntoView({ block: "nearest", behavior: "smooth" });
        },
      },
      textarea,
      error,
      h("div", { class: "form-actions" }, count, submit)
    );

    app.append(form, h("h2", { class: "comments-title" }, "コメント"), list);
    renderComments();

    if (params.get("focus") === "comments") {
      form.scrollIntoView({ block: "start" });
      textarea.focus({ preventScroll: true });
    }
  }

  // =========================================================================
  // SC-05 プロフィール（F-16, F-19, F-20 ほか）
  // =========================================================================

  function viewProfile(params, username) {
    const user = userByName(username);
    if (!user) {
      app.append(h("div", { class: "empty" }, "このユーザーは存在しません"));
      return;
    }
    const isMe = user.id === session;

    const postsStat = h("strong");
    const followingStat = h("strong");
    const followerStat = h("strong");
    const paintStats = () => {
      postsStat.textContent = String(postCount(user.id));
      followingStat.textContent = String(followingCount(user.id));
      followerStat.textContent = String(followerCount(user.id));
    };

    app.append(
      h(
        "section",
        { class: "section" },
        h(
          "div",
          { class: "profile-head" },
          h("div", { class: "profile-avatar" }, avatar(user, 96)),
          h(
            "div",
            { class: "names" },
            h("h1", { class: "profile-name" }, user.displayName),
            h(
              "div",
              { class: "muted" },
              "@" + user.username,
              !isMe && isFollowing(user.id, session) ? h("span", { class: "followed-badge" }, "フォローされています") : null
            )
          ),
          isMe
            ? h("a", { class: "btn btn-outline", href: "#/settings/profile" }, "プロフィールを編集")
            : followButton(user, paintStats)
        ),
        user.bio ? h("p", { class: "profile-bio" }, user.bio) : null,
        h("p", { class: "profile-since" }, sinceText(user.createdAt)),
        h(
          "div",
          { class: "profile-stats" },
          h("span", null, "投稿 ", postsStat, "件"),
          h("span", null, followingStat, " フォロー中"),
          h("span", null, followerStat, " フォロワー")
        )
      ),
      pagedList(
        (cursor) => fetchPosts((p) => p.userId === user.id, cursor),
        (p) => postCard(p, { onDelete: paintStats }),
        h("div", { class: "empty" }, "まだ投稿がありません")
      )
    );
    paintStats();
  }

  // =========================================================================
  // SC-06 プロフィール編集（F-18）
  // =========================================================================

  function viewProfileEdit() {
    const user = me();
    const backToProfile = () => go(`#/users/${encodeURIComponent(user.username)}`);

    const nameInput = h("input", { class: "input", id: "edit-name", value: user.displayName });
    const nameErr = h("p", { class: "error", id: "err-name" });
    const bioInput = h("textarea", { class: "textarea", id: "edit-bio", rows: "4" });
    bioInput.value = user.bio || "";
    const bioErr = h("p", { class: "error", id: "err-bio" });
    const bioCount = counter(BIO_MAX);
    const save = h("button", { type: "submit", class: "btn btn-primary" }, "保存する");

    // アイコン：選んだ画像・削除はプレビューにだけ反映し、[保存する] で確定する
    let pendingAvatar = user.avatar || null;
    const avatarPreview = h("div", { class: "avatar-preview" });
    const avatarErr = h("p", { class: "error", id: "err-avatar", role: "alert" });
    const avatarFile = h("input", { type: "file", accept: IMAGE_TYPES.join(","), hidden: true });
    const chooseBtn = h("button", { type: "button", class: "btn btn-outline", onClick: () => avatarFile.click() }, "画像を選択");
    const removeBtn = h(
      "button",
      {
        type: "button",
        class: "btn btn-danger-text",
        onClick: () => {
          pendingAvatar = null;
          avatarErr.textContent = "";
          paintAvatar();
        },
      },
      "アイコンを削除"
    );
    const paintAvatar = () => {
      // 頭文字は入力中の表示名から作る
      const preview = { id: user.id, displayName: nameInput.value.trim() || user.displayName };
      avatarPreview.replaceChildren(avatar(preview, 96, pendingAvatar));
      removeBtn.hidden = !pendingAvatar;
    };
    avatarFile.addEventListener("change", async () => {
      const file = avatarFile.files[0];
      avatarFile.value = "";
      if (!file) return;
      avatarErr.textContent = "";
      if (!IMAGE_TYPES.includes(file.type)) return (avatarErr.textContent = "JPEG・PNG・GIF・WebP の画像を選択してください");
      if (file.size > IMAGE_MAX_BYTES) return (avatarErr.textContent = "5MB 以下の画像を選択してください");
      try {
        pendingAvatar = await squareImage(file);
        paintAvatar();
      } catch (e) {
        avatarErr.textContent = "JPEG・PNG・GIF・WebP の画像を選択してください";
      }
    });
    nameInput.addEventListener("input", paintAvatar);

    const refresh = () => {
      const n = len(bioInput.value.trim());
      bioCount.update(n);
      save.disabled = n > BIO_MAX;
    };
    bioInput.addEventListener("input", refresh);

    app.append(
      h(
        "div",
        { class: "back-bar" },
        h("button", { type: "button", class: "btn btn-outline", onClick: backToProfile }, "← 戻る"),
        h("h1", { class: "page-title" }, "プロフィールを編集")
      ),
      h(
        "form",
        {
          class: "section",
          novalidate: true,
          onSubmit: (e) => {
            e.preventDefault();
            const displayName = nameInput.value.trim();
            const bio = bioInput.value.trim();
            nameErr.textContent = "";
            bioErr.textContent = "";
            if (len(displayName) === 0) nameErr.textContent = "表示名を入力してください";
            else if (len(displayName) > DISPLAY_NAME_MAX) nameErr.textContent = "表示名は 30 文字以内にしてください";
            if (len(bio) > BIO_MAX) bioErr.textContent = "自己紹介は 160 文字以内にしてください";
            if (nameErr.textContent || bioErr.textContent) return;

            // 更新対象は常にログイン中の利用者自身
            user.displayName = displayName;
            user.bio = bio; // 空なら「未設定」扱い（プロフィールで行ごと非表示）
            user.avatar = pendingAvatar; // null なら頭文字アイコン
            saveDb();
            flash("プロフィールを更新しました");
            backToProfile();
          },
        },
        h(
          "div",
          { class: "field" },
          h("label", null, "アイコン"),
          h("div", { class: "avatar-edit" }, avatarPreview, chooseBtn, removeBtn, avatarFile),
          avatarErr
        ),
        h(
          "div",
          { class: "field" },
          h("label", null, "ユーザー名"),
          h("div", null, "@" + user.username, h("span", { class: "muted" }, "（変更できません）"))
        ),
        h("div", { class: "field" }, h("label", { for: "edit-name" }, "表示名"), nameInput, nameErr),
        h(
          "div",
          { class: "field" },
          h("label", { for: "edit-bio" }, "自己紹介"),
          bioInput,
          h("div", { style: "text-align:right" }, bioCount),
          bioErr
        ),
        h(
          "div",
          { class: "form-actions" },
          h("button", { type: "button", class: "btn btn-outline", onClick: backToProfile }, "キャンセル"),
          save
        )
      )
    );
    refresh();
    paintAvatar();
    nameInput.focus();
  }

  /** 画像の中央を正方形に切り抜き、AVATAR_PX 四方に縮小して data URL にする */
  function squareImage(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = reject;
      reader.onload = () => {
        const img = new Image();
        img.onerror = reject;
        img.onload = () => {
          const side = Math.min(img.width, img.height);
          const canvas = document.createElement("canvas");
          canvas.width = canvas.height = Math.min(AVATAR_PX, side);
          const ctx = canvas.getContext("2d");
          ctx.fillStyle = "#fff";
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, canvas.width, canvas.height);
          resolve(canvas.toDataURL("image/jpeg", 0.85));
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  // =========================================================================
  // SC-07 ユーザー検索（F-19, F-22, F-23）
  // =========================================================================

  /** 検索語の正規化：前後の空白と先頭の @ を取り除く */
  function normalizeQuery(q) {
    return (q || "").trim().replace(/^@/, "").trim();
  }

  function searchUsers(term) {
    const t = term.toLowerCase();
    const rank = (u) => {
      const name = u.username.toLowerCase();
      if (name === t) return 0; // 完全一致
      if (name.startsWith(t)) return 1; // 前方一致
      return 2; // その他の部分一致
    };
    return db.users
      .filter((u) => u.username.toLowerCase().includes(t) || u.displayName.toLowerCase().includes(t))
      .map((u) => ({ u, r: rank(u), f: followerCount(u.id) }))
      .sort((a, b) => a.r - b.r || b.f - a.f || b.u.createdAt - a.u.createdAt)
      .map((x) => x.u);
  }

  function recommendedUsers() {
    return db.users
      .filter((u) => u.id !== session && !isFollowing(session, u.id))
      .sort((a, b) => followerCount(b.id) - followerCount(a.id) || b.createdAt - a.createdAt)
      .slice(0, RECOMMEND_MAX);
  }

  function userRow(user) {
    const firstLine = (user.bio || "").split("\n")[0];
    return h(
      "div",
      {
        class: "user-row",
        onClick: (e) => {
          if (e.target.closest("button, a")) return;
          go(`#/users/${encodeURIComponent(user.username)}`);
        },
      },
      avatarLink(user, 40),
      h(
        "div",
        { class: "info" },
        h(
          "a",
          { class: "user-link", href: `#/users/${encodeURIComponent(user.username)}` },
          h("span", { class: "name row-name" }, user.displayName)
        ),
        h(
          "div",
          { class: "muted" },
          "@" + user.username,
          user.id !== session && isFollowing(user.id, session) ? h("span", { class: "followed-badge" }, "フォローされています") : null
        ),
        firstLine ? h("p", { class: "row-bio" }, firstLine) : null
      ),
      user.id === session ? null : followButton(user)
    );
  }

  function viewSearch(params) {
    const raw = params.get("q") || "";
    const term = normalizeQuery(raw);

    const input = h("input", {
      class: "input",
      type: "search",
      placeholder: "@ユーザー名・表示名で検索",
      "aria-label": "ユーザーを検索",
      maxLength: SEARCH_MAX,
      value: raw,
    });
    app.append(
      h(
        "form",
        {
          class: "section search-form",
          role: "search",
          onSubmit: (e) => {
            e.preventDefault();
            const q = normalizeQuery(input.value);
            // 空（空白のみ）なら検索せず、おすすめユーザーを表示
            go(q ? `#/search?q=${encodeURIComponent(input.value.trim())}` : "#/search");
          },
        },
        h("div", { class: "input-prefix" }, h("span", { "aria-hidden": "true" }, "🔍"), input),
        h("button", { type: "submit", class: "btn btn-primary" }, "検索")
      )
    );

    if (!term) {
      // F-23 おすすめユーザー（開いた時点の一覧を表示し、フォローしても消さない）
      const users = recommendedUsers();
      app.append(
        h("div", { class: "list-title" }, "おすすめユーザー"),
        users.length ? h("div", null, users.map(userRow)) : h("div", { class: "empty" }, "おすすめできるユーザーはいません")
      );
      input.focus();
      return;
    }

    // F-22 ユーザー検索（20 件ずつ、ページ番号で続きを取る）
    const results = searchUsers(term);
    app.append(
      h("div", { class: "list-title" }, `「${term}」の検索結果`),
      pagedList(
        (page) => {
          const p = page || 0;
          const items = results.slice(p * PAGE_SIZE, (p + 1) * PAGE_SIZE);
          return { items, next: results.length > (p + 1) * PAGE_SIZE ? p + 1 : null };
        },
        userRow,
        h("div", { class: "empty" }, `「${term}」に一致するユーザーは見つかりませんでした`)
      )
    );
  }

  // =========================================================================
  // データのリセット・起動
  // =========================================================================

  document.getElementById("reset-data").addEventListener("click", () => {
    if (!confirm("データを初期状態に戻しますか？（追加した投稿・ユーザーなどはすべて消え、ログアウトします）")) return;
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (e) {
      /* 何もしない */
    }
    db = createSeed();
    saveDb();
    setSession(null);
    flash("データを初期状態に戻しました");
    go("#/login");
  });

  // 他のタブでの変更（別ユーザーとしてのログインなど）を反映する
  window.addEventListener("storage", (e) => {
    if (e.key === STORAGE_KEY || e.key === SESSION_KEY) {
      db = loadDb();
      session = loadSession();
      render();
    }
  });

  if (!safeHas(STORAGE_KEY)) saveDb();
  render();

  function safeHas(key) {
    try {
      return localStorage.getItem(key) !== null;
    } catch (e) {
      return true; // 使えない環境では保存を試みない
    }
  }
})();
