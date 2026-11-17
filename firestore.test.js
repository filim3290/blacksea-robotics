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
  await assertFails(unauthedDb.collection("teams").where("status", "==", "approved").get());
});

test("Authenticated user can create own base profile without privilege escalation", async () => {
  const authedDb = testEnv.authenticatedContext("user1", { email: "user1@example.com", email_verified: true }).firestore();
  await assertSucceeds(
    authedDb.collection("users").doc("user1").set({
      userId: "user1",
      displayName: "Ahmet Yılmaz",
      email: "user1@example.com",
      team: "Genel Ekip",
      role: "Takım Üyesi",
      status: "active",
      isSuperAdmin: false,
      isPlatformAdmin: false,
      photoUrl: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Cannot escalate self to platform admin
  await assertFails(
    authedDb.collection("users").doc("user1_bad").set({
      userId: "user1_bad",
      displayName: "Hacker",
      email: "hacker@example.com",
      isPlatformAdmin: true,
      isSuperAdmin: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
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
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );
});

test("Spoofed unverified founder email claim is denied platform admin access", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_u", {
    email: "filim3290@gmail.com",
    email_verified: false
  }).firestore();
  await assertFails(spoofDb.collection("teams").get());

  const realFounderDb = testEnv.authenticatedContext("founder_u", {
    email: "filim3290@gmail.com",
    email_verified: true
  }).firestore();
  await assertSucceeds(realFounderDb.collection("teams").get());
});

test("Active approved BSR user can send message in root room", async () => {
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
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
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
      teamId: "blacksea_robotics",
      recipientUserId: "managers",
      type: "member_pending",
      senderId: "applicant_u",
      title: "Yeni Üye Başvurusu",
      message: "Onay bekliyor"
    })
  );
  await assertFails(
    applicantDb.collection("notifications").doc("notif_spam_1").set({
      teamId: "blacksea_robotics",
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

  await assertFails(memberDb.collection("users").doc("mgr1").delete());
  await assertFails(mgrDb.collection("users").doc("founder1").delete());
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
      teamId: "blacksea_robotics",
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

test("Legacy user document without status field can read and send messages in BSR", async () => {
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

test("Message sender can edit text within 1 hour, denied after 1 hour, and non-owner cannot edit text", async () => {
  const now = Date.now();
  const twentyMinutesAgo = new Date(now - 20 * 60 * 1000);
  const twoHoursAgo = new Date(now - 2 * 60 * 60 * 1000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc("sender_u").set({
      userId: "sender_u",
      email: "sender@example.com",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "approved",
      isSuperAdmin: false
    });
    await db.collection("users").doc("mgr_u").set({
      userId: "mgr_u",
      email: "mgr@example.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "approved",
      isSuperAdmin: true
    });
    await db.collection("rooms").doc("general").collection("messages").doc("fresh_msg").set({
      roomId: "general",
      senderId: "sender_u",
      senderName: "Gönderen",
      text: "İlk metin",
      isPinned: false,
      createdAt: twentyMinutesAgo,
      updatedAt: twentyMinutesAgo
    });
    await db.collection("rooms").doc("general").collection("messages").doc("old_msg").set({
      roomId: "general",
      senderId: "sender_u",
      senderName: "Gönderen",
      text: "Eski metin",
      isPinned: false,
      createdAt: twoHoursAgo,
      updatedAt: twoHoursAgo
    });
  });

  const senderDb = testEnv.authenticatedContext("sender_u", { email: "sender@example.com" }).firestore();
  const mgrDb = testEnv.authenticatedContext("mgr_u", { email: "mgr@example.com" }).firestore();

  await assertSucceeds(
    senderDb.collection("rooms").doc("general").collection("messages").doc("fresh_msg").update({
      text: "Düzenlenmiş metin",
      isEdited: true,
      editedAt: new Date(now),
      updatedAt: new Date(now)
    })
  );

  await assertFails(
    senderDb.collection("rooms").doc("general").collection("messages").doc("fresh_msg").update({
      text: "",
      isEdited: true,
      editedAt: new Date(now),
      updatedAt: new Date(now)
    })
  );

  await assertFails(
    senderDb.collection("rooms").doc("general").collection("messages").doc("old_msg").update({
      text: "Geç düzenleme",
      isEdited: true,
      editedAt: new Date(now),
      updatedAt: new Date(now)
    })
  );

  await assertFails(
    mgrDb.collection("rooms").doc("general").collection("messages").doc("fresh_msg").update({
      text: "Yönetici değiştirdi",
      isEdited: true,
      editedAt: new Date(now),
      updatedAt: new Date(now)
    })
  );

  await assertSucceeds(
    mgrDb.collection("rooms").doc("general").collection("messages").doc("old_msg").update({
      isPinned: true,
      updatedAt: new Date(now)
    })
  );
});

test("Support tickets: owner creates and lists own, managers update reply/status, cross-user access denied", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc("member_a").set({
      userId: "member_a",
      email: "a@example.com",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "approved",
      isSuperAdmin: false
    });
    await db.collection("users").doc("member_b").set({
      userId: "member_b",
      email: "b@example.com",
      role: "Mekanik Üyesi",
      roles: ["Mekanik Üyesi"],
      status: "approved",
      isSuperAdmin: false
    });
    await db.collection("users").doc("mgr_s").set({
      userId: "mgr_s",
      email: "mgrs@example.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "approved",
      isSuperAdmin: true
    });
  });

  const memberADb = testEnv.authenticatedContext("member_a", { email: "a@example.com" }).firestore();
  const memberBDb = testEnv.authenticatedContext("member_b", { email: "b@example.com" }).firestore();
  const mgrDb = testEnv.authenticatedContext("mgr_s", { email: "mgrs@example.com" }).firestore();

  await assertSucceeds(
    memberADb.collection("support_tickets").doc("ticket_1").set({
      userId: "member_a",
      userName: "Ahmet Yılmaz",
      userEmail: "a@example.com",
      userTeam: "Yazılım Ekibi",
      category: "Bildirim Sorunu",
      subject: "Arka plan bildirimi gecikiyor",
      description: "Uygulama arka plandayken yeni sohbet mesajları geç düşüyor.",
      appVersion: "1.0.12 (13)",
      deviceInfo: "Android 14 (SDK 34)",
      status: "open",
      adminReply: "",
      repliedByName: "",
      createdAt: created,
      updatedAt: created
    })
  );

  await assertFails(
    memberADb.collection("support_tickets").doc("ticket_bad_status").set({
      userId: "member_a",
      userName: "Ahmet Yılmaz",
      userEmail: "a@example.com",
      userTeam: "Yazılım Ekibi",
      category: "Bildirim Sorunu",
      subject: "Test konu",
      description: "En az on karakterlik açıklama metni.",
      appVersion: "1.0.12 (13)",
      deviceInfo: "Android 14 (SDK 34)",
      status: "resolved",
      adminReply: "Sahte yanıt",
      repliedByName: "",
      createdAt: created,
      updatedAt: created
    })
  );

  await assertSucceeds(memberADb.collection("support_tickets").where("userId", "==", "member_a").get());
  await assertFails(memberBDb.collection("support_tickets").doc("ticket_1").get());
  await assertFails(memberBDb.collection("support_tickets").get());
  await assertFails(memberBDb.collection("support_tickets").where("userId", "==", "member_a").get());

  await assertSucceeds(mgrDb.collection("support_tickets").get());
  await assertSucceeds(
    mgrDb.collection("support_tickets").doc("ticket_1").update({
      status: "resolved",
      adminReply: "Arka plan servisi güncellendi, pil kısıtlamasını kapatabilirsiniz.",
      repliedByName: "Yönetici",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
});

// =========================================================================
// MULTI-TEAM PLATFORM TESTS (TEAM APPLICATIONS, APPROVALS, ISOLATION)
// =========================================================================

test("New team creation requires pending status and BlackSea Robotics platform admin approval", async () => {
  const now = new Date(Date.now() - 2000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc("fatsa_founder").set({
      userId: "fatsa_founder",
      email: "fatsa@example.com",
      displayName: "Zeynep Kaya",
      status: "active",
      isSuperAdmin: false,
      isPlatformAdmin: false
    });
    await db.collection("users").doc("bsr_admin").set({
      userId: "bsr_admin",
      email: "bsr@example.com",
      displayName: "BSR Kaptan",
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: true
    });
  });

  const founderDb = testEnv.authenticatedContext("fatsa_founder", { email: "fatsa@example.com" }).firestore();
  const bsrAdminDb = testEnv.authenticatedContext("bsr_admin", { email: "bsr@example.com" }).firestore();
  const randomStudentDb = testEnv.authenticatedContext("student_x", { email: "x@example.com" }).firestore();

  // Cannot self-create an already approved team
  await assertFails(
    founderDb.collection("teams").doc("team_fatsa_hack").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "TEKNOFEST Robotik Takımı",
      managerName: "Zeynep Kaya",
      managerContact: "fatsa@example.com",
      founderId: "fatsa_founder",
      founderEmail: "fatsa@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 1,
      createdAt: now,
      updatedAt: now
    })
  );

  // Valid pending team application succeeds + platform_admin notification succeeds
  await assertSucceeds(
    founderDb.collection("teams").doc("team_fatsa").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "TEKNOFEST için kurulan okul robotik ekibi.",
      motto: "Geleceği Kodla",
      logoKey: "rocket",
      accentColorHex: "#7C3AED",
      managerName: "Zeynep Kaya",
      managerContact: "0555 111 2233",
      founderId: "fatsa_founder",
      founderEmail: "fatsa@example.com",
      status: "pending",
      isMainTeam: false,
      memberCount: 1,
      rejectionReason: "",
      reviewedBy: "",
      createdAt: now,
      updatedAt: now
    })
  );

  await assertSucceeds(
    founderDb.collection("notifications").doc("notif_team_app_1").set({
      teamId: "platform_admin",
      teamName: "BlackSea Robotics Platform",
      recipientUserId: "platform_admins",
      type: "team_application_pending",
      targetId: "team_fatsa",
      senderId: "fatsa_founder",
      senderName: "Zeynep Kaya",
      title: "Yeni Takım Kurma Başvurusu",
      message: "Fatsa Robotik onay bekliyor",
      isRead: false,
      createdAt: now
    })
  );

  // Pending team is hidden from random students, visible to founder & BSR platform admins
  await assertFails(randomStudentDb.collection("teams").doc("team_fatsa").get());
  await assertSucceeds(founderDb.collection("teams").where("founderId", "==", "fatsa_founder").get());
  await assertSucceeds(bsrAdminDb.collection("teams").get());

  // Founder cannot self-approve
  await assertFails(
    founderDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // BSR platform admin approves Fatsa Robotik and creates founder membership
  await assertSucceeds(
    bsrAdminDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      reviewedBy: "bsr_admin",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    bsrAdminDb.collection("team_memberships").doc("team_fatsa_fatsa_founder").set({
      teamId: "team_fatsa",
      userId: "fatsa_founder",
      userName: "Zeynep Kaya",
      userEmail: "fatsa@example.com",
      userPhotoUrl: "",
      subTeam: "Genel Yönetim",
      role: "Yönetici",
      roles: ["Yönetici", "Takım Kaptanı"],
      status: "active",
      isTeamAdmin: true,
      joinNote: "Takım kurucu yöneticisi",
      createdAt: now,
      updatedAt: now
    })
  );

  // Once approved, random student can see the approved team in directory and request membership
  await assertSucceeds(randomStudentDb.collection("teams").where("status", "==", "approved").get());
  await assertSucceeds(
    randomStudentDb.collection("team_memberships").doc("team_fatsa_student_x").set({
      teamId: "team_fatsa",
      userId: "student_x",
      userName: "Öğrenci X",
      userEmail: "x@example.com",
      userPhotoUrl: "",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "pending",
      isTeamAdmin: false,
      joinNote: "Katılmak istiyorum",
      createdAt: now,
      updatedAt: now
    })
  );

  // Student cannot self-approve own membership
  await assertFails(
    randomStudentDb.collection("team_memberships").doc("team_fatsa_student_x").update({
      status: "active",
      isTeamAdmin: true,
      updatedAt: new Date(Date.now() - 500)
    })
  );
});

test("Complete workspace isolation across teams (messages, files, ideas, tasks, announcements, notifications, support)", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("team_fatsa").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "Fatsa ekibi",
      managerName: "Fatsa Kaptan",
      managerContact: "fk@example.com",
      founderId: "fatsa_mgr",
      founderEmail: "fk@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_unye").set({
      name: "Ünye Yapay Zeka",
      shortCode: "UNYE",
      competition: "TEKNOFEST",
      schoolName: "Ünye Anadolu Lisesi",
      city: "Ordu",
      description: "Ünye ekibi",
      managerName: "Ünye Kaptan",
      managerContact: "uk@example.com",
      founderId: "unye_mgr",
      founderEmail: "uk@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_fatsa_fatsa_mgr").set({
      teamId: "team_fatsa",
      userId: "fatsa_mgr",
      userName: "Fatsa Kaptan",
      userEmail: "fk@example.com",
      subTeam: "Genel Yönetim",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_unye_unye_member").set({
      teamId: "team_unye",
      userId: "unye_member",
      userName: "Ünye Üyesi",
      userEmail: "um@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("users").doc("bsr_admin_only").set({
      userId: "bsr_admin_only",
      email: "bsradmin@example.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isPlatformAdmin: true,
      isSuperAdmin: true
    });

    // Seed isolated data inside team_fatsa workspace
    await db.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").doc("f_msg_1").set({
      roomId: "general",
      teamId: "team_fatsa",
      senderId: "fatsa_mgr",
      senderName: "Fatsa Kaptan",
      text: "Gizli strateji",
      isPinned: false,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_fatsa").collection("tasks").doc("f_task_1").set({
      teamId: "team_fatsa",
      title: "Şasi montajı",
      subTeam: "Mekanik Ekibi",
      createdById: "fatsa_mgr",
      createdByName: "Fatsa Kaptan",
      status: "todo",
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_fatsa").collection("files").doc("f_file_1").set({
      roomId: "general",
      teamId: "team_fatsa",
      uploadedById: "fatsa_mgr",
      fileName: "cad.step"
    });
    await db.collection("notifications").doc("notif_fatsa_1").set({
      teamId: "team_fatsa",
      recipientUserId: "all",
      senderId: "fatsa_mgr",
      type: "message",
      title: "Fatsa Mesaj",
      message: "Test"
    });
  });

  const fatsaMgrDb = testEnv.authenticatedContext("fatsa_mgr", { email: "fk@example.com" }).firestore();
  const unyeMemberDb = testEnv.authenticatedContext("unye_member", { email: "um@example.com" }).firestore();
  const bsrAdminDb = testEnv.authenticatedContext("bsr_admin_only", { email: "bsradmin@example.com" }).firestore();

  // Fatsa manager CAN access team_fatsa workspace and create tasks
  await assertSucceeds(fatsaMgrDb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").get());
  await assertSucceeds(fatsaMgrDb.collection("teams").doc("team_fatsa").collection("tasks").get());
  await assertSucceeds(fatsaMgrDb.collection("notifications").where("teamId", "==", "team_fatsa").get());

  // Fatsa manager CANNOT access BSR admin functions or BSR root collections
  await assertFails(fatsaMgrDb.collection("rooms").doc("general").collection("messages").get());
  await assertFails(fatsaMgrDb.collection("teams").doc("team_unye").update({ status: "rejected" }));

  // Ünye member CANNOT read/write Fatsa messages, tasks, files, memberships, or notifications
  await assertFails(unyeMemberDb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").get());
  await assertFails(unyeMemberDb.collection("teams").doc("team_fatsa").collection("tasks").get());
  await assertFails(unyeMemberDb.collection("teams").doc("team_fatsa").collection("files").get());
  await assertFails(unyeMemberDb.collection("notifications").where("teamId", "==", "team_fatsa").get());
  await assertFails(unyeMemberDb.collection("team_memberships").where("teamId", "==", "team_fatsa").get());

  // Even BlackSea Robotics Platform Admin CANNOT snoop on Fatsa's private messages, files, ideas, or tasks
  await assertFails(bsrAdminDb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").get());
  await assertFails(bsrAdminDb.collection("teams").doc("team_fatsa").collection("tasks").get());
  await assertFails(bsrAdminDb.collection("teams").doc("team_fatsa").collection("files").get());
  await assertSucceeds(bsrAdminDb.collection("teams").get()); // Can view team registry/status summary
});
