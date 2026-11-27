const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("posts").where("authorHidden", "==", false).get());
  await assertFails(unauthDb.collection("profiles").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("aliases").doc("alice_dev").get());
  await assertFails(
    unauthDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorAlias: "alice_dev",
      authorHidden: false,
      circle: "DAILY_CALM",
      caption: "Unauthorized",
      reactionCount: 0,
      commentCount: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Onboarding: alias reservation, private user PII, and public profile creation & isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("aliases").doc("alice_dev").set({
      uid: ALICE_UID,
      createdAt: ts,
    })
  );

  // Bob cannot claim Alice's UID or invalid alias
  await assertFails(
    bobDb.collection("aliases").doc("BAD!ALIAS").set({
      uid: BOB_UID,
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's private PII user doc
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      uid: ALICE_UID,
      alias: "alice_dev",
      displayName: "Alice",
      bio: "Quiet mornings and film cameras.",
      profileHidden: false,
      tagPermission: "EVERYONE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can read Alice's public profile when profileHidden == false
  await assertSucceeds(bobDb.collection("profiles").doc(ALICE_UID).get());

  // Bob cannot modify Alice's profile
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      bio: "Hijacked bio",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice hides her profile -> Bob can no longer get or list it
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      profileHidden: true,
      tagPermission: "NOBODY",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("profiles").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("profiles").doc(ALICE_UID).get());
});

test("Posts: Kotlin create, @DocumentId guard, chronological feed query, reactions, comments, and reports", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Mirror Kotlin createPost payload (no "id" field)
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorAlias: "alice_dev",
      authorHidden: false,
      circle: "PHOTOGRAPHY",
      caption: "Golden hour through the window.",
      reactionCount: 0,
      commentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject ghost field & @DocumentId "id" field
  await assertFails(
    aliceDb.collection("posts").doc("post_2").set({
      id: "post_2",
      authorId: ALICE_UID,
      authorAlias: "alice_dev",
      authorHidden: false,
      circle: "PHOTOGRAPHY",
      caption: "Should fail",
      reactionCount: 0,
      commentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("posts").doc("post_3").set({
      authorId: ALICE_UID,
      authorAlias: "alice_dev",
      authorHidden: false,
      circle: "DAILY_CALM",
      caption: "Future post",
      reactionCount: 0,
      commentCount: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Unfiltered posts list query must fail; aligned query succeeds
  await assertFails(bobDb.collection("posts").get());
  await assertSucceeds(bobDb.collection("posts").where("authorHidden", "==", false).get());
  await assertSucceeds(aliceDb.collection("posts").where("authorId", "==", ALICE_UID).get());

  // Bob reacts to post_1 (atomic +1 reactionCount, reaction doc, likedPosts index, alert)
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("reactions").doc(BOB_UID).set({
      uid: BOB_UID,
      alias: "bob_reads",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("likedPosts").doc("post_1").set({
      postId: "post_1",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      reactionCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Bob cannot jump reactionCount by +10
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      reactionCount: 10,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob adds a comment and sends alert to Alice
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1").set({
      postId: "post_1",
      postAuthorId: ALICE_UID,
      postHidden: false,
      authorId: BOB_UID,
      authorAlias: "bob_reads",
      body: "Love the light here.",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      commentCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("users").doc(ALICE_UID).collection("alerts").doc("a_1").set({
      recipientUid: ALICE_UID,
      actorUid: BOB_UID,
      actorAlias: "bob_reads",
      type: "COMMENT",
      postId: "post_1",
      postPreview: "Golden hour through the window.",
      commentPreview: "Love the light here.",
      read: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read Alice's alerts; Alice can read & mark read
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("alerts").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("alerts").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("alerts").doc("a_1").update({ read: true })
  );

  // Bob queries comments aligned with rules
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").where("postHidden", "==", false).get()
  );

  // Bob can delete his own comment and decrement commentCount by 1, but cannot set negative commentCount
  await assertFails(
    bobDb.collection("posts").doc("post_1").update({
      commentCount: -1,
      updatedAt: new Date(Date.now() - 800),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1").delete()
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").update({
      commentCount: 0,
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Bob reports post_1, can list his own reports, cannot read Alice's reports
  await assertSucceeds(
    bobDb.collection("reports").doc("rep_1").set({
      reporterId: BOB_UID,
      targetPostId: "post_1",
      targetAuthorId: ALICE_UID,
      reason: "Spam or off-topic",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("reports").where("reporterId", "==", BOB_UID).get()
  );
  await assertFails(
    aliceDb.collection("reports").where("reporterId", "==", BOB_UID).get()
  );
  await assertFails(aliceDb.collection("reports").doc("rep_1").get());

  // Bob blocks Alice; Alice cannot read Bob's blocklist
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("blocks").doc(ALICE_UID).set({
      blockedUid: ALICE_UID,
      blockedAlias: "alice_dev",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("blocks").get());
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("blocks").get());
});
