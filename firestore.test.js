const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-no-project",
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

test("Unauthenticated read is rejected", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("users").doc("user1").get());
});

test("Authenticated user can create own profile", async () => {
  const authedDb = testEnv.authenticatedContext("user1", { email: "user1@example.com" }).firestore();
  await assertSucceeds(
    authedDb.collection("users").doc("user1").set({
      userId: "user1",
      displayName: "Ahmet Yılmaz",
      email: "user1@example.com",
      team: "Yazılım Ekibi",
      role: "Takım Üyesi",
      status: "approved",
      photoUrl: "",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Authenticated user cannot create profile for another user", async () => {
  const authedDb = testEnv.authenticatedContext("user1", { email: "user1@example.com" }).firestore();
  await assertFails(
    authedDb.collection("users").doc("user2").set({
      userId: "user2",
      displayName: "Mehmet Demir",
      email: "user2@example.com",
      team: "Mekanik Ekibi",
      role: "Takım Üyesi",
      status: "pending",
      photoUrl: "",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Active approved user can send message in room", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("user1").set({
      userId: "user1",
      displayName: "Ahmet Yılmaz",
      email: "user1@example.com",
      team: "Yazılım Ekibi",
      role: "Takım Üyesi",
      roles: ["Takım Üyesi"],
      status: "approved"
    });
  });

  const authedDb = testEnv.authenticatedContext("user1", { email: "user1@example.com" }).firestore();
  await assertSucceeds(
    authedDb.collection("rooms").doc("general").collection("messages").doc("msg1").set({
      roomId: "general",
      senderId: "user1",
      senderName: "Ahmet",
      senderTeam: "Yazılım Ekibi",
      text: "Merhaba takım!",
      isPinned: false,
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("Revoked or pending user cannot read notifications or send room messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("revoked_u").set({
      userId: "revoked_u",
      email: "revoked@example.com",
      status: "revoked"
    });
    await ctx.firestore().collection("users").doc("pending_u").set({
      userId: "pending_u",
      email: "pending@example.com",
      status: "pending"
    });
  });

  const revokedDb = testEnv.authenticatedContext("revoked_u", { email: "revoked@example.com" }).firestore();
  await assertFails(revokedDb.collection("notifications").get());
  await assertFails(
    revokedDb.collection("rooms").doc("general").collection("messages").doc("m_rev").set({
      roomId: "general",
      senderId: "revoked_u",
      text: "blocked"
    })
  );

  const pendingDb = testEnv.authenticatedContext("pending_u", { email: "pending@example.com" }).firestore();
  await assertFails(pendingDb.collection("rooms").doc("general").collection("messages").get());
});

test("Pending or revoked user can reapply and send member_pending notification to managers", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("applicant_u").set({
      userId: "applicant_u",
      email: "applicant@example.com",
      status: "revoked",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      isSuperAdmin: false
    });
  });

  const applicantDb = testEnv.authenticatedContext("applicant_u", { email: "applicant@example.com" }).firestore();
  await assertSucceeds(
    applicantDb.collection("users").doc("applicant_u").update({
      status: "pending"
    })
  );
  await assertFails(
    applicantDb.collection("users").doc("applicant_u").update({
      status: "approved"
    })
  );
  await assertSucceeds(
    applicantDb.collection("notifications").doc("notif_pending_1").set({
      recipientUserId: "managers",
      type: "member_pending",
      senderId: "applicant_u",
      title: "Yeni Üye Başvurusu",
      message: "Onay bekliyor"
    })
  );
  await assertFails(
    applicantDb.collection("notifications").doc("notif_spam_1").set({
      recipientUserId: "all",
      type: "broadcast",
      senderId: "applicant_u",
      title: "Spam",
      message: "Blocked"
    })
  );
});

test("Active manager can revoke or delete non-self/non-founder users while normal users cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("mgr1").set({
      userId: "mgr1",
      email: "mgr1@example.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "approved",
      isSuperAdmin: true
    });
    await ctx.firestore().collection("users").doc("member1").set({
      userId: "member1",
      email: "member1@example.com",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "approved",
      isSuperAdmin: false
    });
    await ctx.firestore().collection("users").doc("founder1").set({
      userId: "founder1",
      email: "filim3290@gmail.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isSuperAdmin: true
    });
  });

  const mgrDb = testEnv.authenticatedContext("mgr1", { email: "mgr1@example.com" }).firestore();
  const memberDb = testEnv.authenticatedContext("member1", { email: "member1@example.com" }).firestore();

  // Normal user cannot delete another user
  await assertFails(memberDb.collection("users").doc("mgr1").delete());

  // Manager cannot delete founder
  await assertFails(mgrDb.collection("users").doc("founder1").delete());

  // Manager can revoke and delete regular member
  await assertSucceeds(mgrDb.collection("users").doc("member1").update({ status: "revoked" }));
  await assertSucceeds(mgrDb.collection("users").doc("member1").delete());
});

test("Any user can delete own account and personal notifications", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("self_u").set({
      userId: "self_u",
      email: "self@example.com",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "approved",
      isSuperAdmin: false
    });
    await ctx.firestore().collection("notifications").doc("self_n1").set({
      recipientUserId: "self_u",
      type: "member_approved",
      title: "Onay",
      message: "Hoş geldiniz"
    });
  });

  const selfDb = testEnv.authenticatedContext("self_u", { email: "self@example.com" }).firestore();
  await assertSucceeds(selfDb.collection("notifications").doc("self_n1").delete());
  await assertSucceeds(selfDb.collection("users").doc("self_u").delete());
});

test("Legacy user document without status field can read and send messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("legacy_u").set({
      userId: "legacy_u",
      displayName: "Eski Sürüm Üye",
      email: "legacy@example.com",
      team: "Mekanik Ekibi",
      role: "Mekanik Üyesi"
    });
  });

  const legacyDb = testEnv.authenticatedContext("legacy_u", { email: "legacy@example.com" }).firestore();
  await assertSucceeds(legacyDb.collection("rooms").doc("general").collection("messages").get());
  await assertSucceeds(
    legacyDb.collection("rooms").doc("general").collection("messages").doc("legacy_msg").set({
      roomId: "general",
      senderId: "legacy_u",
      senderName: "Eski Sürüm Üye",
      senderTeam: "Mekanik Ekibi",
      text: "Eski sürümden mesaj!"
    })
  );
});
