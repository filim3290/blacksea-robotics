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

test("Platform founder can revoke or delete non-founder users; Yetkili Öğretmen can revoke BSR membership without deleting global user", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc("teacher1").set({
      userId: "teacher1",
      email: "teacher1@example.com",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
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
      isSuperAdmin: true,
      isPlatformAdmin: true
    });
  });

  const teacherDb = testEnv.authenticatedContext("teacher1", { email: "teacher1@example.com", email_verified: true }).firestore();
  const founderDb = testEnv.authenticatedContext("founder1", { email: "filim3290@gmail.com", email_verified: true }).firestore();
  const memberDb = testEnv.authenticatedContext("member1", { email: "member1@example.com", email_verified: true }).firestore();

  await assertFails(memberDb.collection("users").doc("teacher1").delete());
  await assertFails(teacherDb.collection("users").doc("founder1").delete());
  await assertFails(teacherDb.collection("users").doc("member1").delete());
  // Yetkili Öğretmen cannot escalate role to 'Yönetici'
  await assertFails(teacherDb.collection("users").doc("member1").update({ role: "Yönetici", roles: ["Yönetici"] }));
  await assertSucceeds(teacherDb.collection("users").doc("member1").update({ status: "revoked" }));
  await assertSucceeds(founderDb.collection("users").doc("member1").delete());
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

test("Support tickets: owner creates and lists own, ONLY BlackSea founder (filim3290@gmail.com) reads/replies all, non-founder teachers & cross-users denied", async () => {
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
    await db.collection("users").doc("teacher_s").set({
      userId: "teacher_s",
      email: "teachers@example.com",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "approved",
      isSuperAdmin: true
    });
    await db.collection("users").doc("founder_s").set({
      userId: "founder_s",
      email: "filim3290@gmail.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: true
    });
  });

  const memberADb = testEnv.authenticatedContext("member_a", { email: "a@example.com", email_verified: true }).firestore();
  const memberBDb = testEnv.authenticatedContext("member_b", { email: "b@example.com", email_verified: true }).firestore();
  const teacherDb = testEnv.authenticatedContext("teacher_s", { email: "teachers@example.com", email_verified: true }).firestore();
  const founderDb = testEnv.authenticatedContext("founder_s", { email: "filim3290@gmail.com", email_verified: true }).firestore();

  await assertSucceeds(
    memberADb.collection("support_tickets").doc("ticket_1").set({
      teamId: "blacksea_robotics",
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

  // Team-scoped subcollection support_tickets is disabled
  await assertFails(
    memberADb.collection("teams").doc("team_fatsa").collection("support_tickets").doc("t_sub").set({
      userId: "member_a",
      subject: "Test"
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

  // Non-founder Yetkili Öğretmen CANNOT read or reply to centralized support tickets
  await assertFails(teacherDb.collection("support_tickets").get());
  await assertFails(
    teacherDb.collection("support_tickets").doc("ticket_1").update({
      status: "resolved",
      adminReply: "Yetkisiz yanıt",
      repliedByName: "Öğretmen",
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Platform founder (filim3290@gmail.com) CAN read and reply to all support tickets
  await assertSucceeds(founderDb.collection("support_tickets").get());
  await assertSucceeds(
    founderDb.collection("support_tickets").doc("ticket_1").update({
      status: "resolved",
      adminReply: "Arka plan servisi güncellendi, bildirimler anlık iletiliyor.",
      repliedByName: "Yönetici",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
});

// =========================================================================
// MULTI-TEAM PLATFORM TESTS (TEAM APPLICATIONS, APPROVALS, ISOLATION)
// =========================================================================

test("New team creation requires pending status and BlackSea Robotics founder (filim3290@gmail.com) approval", async () => {
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
    await db.collection("users").doc("bsr_teacher").set({
      userId: "bsr_teacher",
      email: "teacher@example.com",
      displayName: "BSR Öğretmen",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: false
    });
    await db.collection("users").doc("bsr_admin").set({
      userId: "bsr_admin",
      email: "filim3290@gmail.com",
      displayName: "BSR Ana Yönetici",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: true
    });
  });

  const founderDb = testEnv.authenticatedContext("fatsa_founder", { email: "fatsa@example.com", email_verified: true }).firestore();
  const bsrTeacherDb = testEnv.authenticatedContext("bsr_teacher", { email: "teacher@example.com", email_verified: true }).firestore();
  const bsrAdminDb = testEnv.authenticatedContext("bsr_admin", { email: "filim3290@gmail.com", email_verified: true }).firestore();
  const randomStudentDb = testEnv.authenticatedContext("student_x", { email: "x@example.com", email_verified: true }).firestore();

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

  // Pending team is hidden from random students and non-founder teachers, visible to founder & BSR founder platform admin
  await assertFails(randomStudentDb.collection("teams").doc("team_fatsa").get());
  await assertFails(bsrTeacherDb.collection("teams").doc("team_fatsa").get());
  await assertSucceeds(founderDb.collection("teams").where("founderId", "==", "fatsa_founder").get());
  await assertSucceeds(bsrAdminDb.collection("teams").get());

  // Founder and non-founder Yetkili Öğretmen CANNOT approve teams
  await assertFails(
    founderDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertFails(
    bsrTeacherDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // BSR founder platform admin approves Fatsa Robotik and creates Yetkili Öğretmen membership
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
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen", "Takım Kaptanı"],
      status: "active",
      isTeamAdmin: true,
      joinNote: "Takım kurucu yetkilisi",
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

  // Team Yetkili Öğretmen cannot assign 'Yönetici' role to members
  await assertFails(
    founderDb.collection("team_memberships").doc("team_fatsa_student_x").update({
      status: "active",
      role: "Yönetici",
      roles: ["Yönetici"],
      isTeamAdmin: true,
      updatedAt: new Date(Date.now() - 500)
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
      email: "filim3290@gmail.com",
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

  const fatsaMgrDb = testEnv.authenticatedContext("fatsa_mgr", { email: "fk@example.com", email_verified: true }).firestore();
  const unyeMemberDb = testEnv.authenticatedContext("unye_member", { email: "um@example.com", email_verified: true }).firestore();
  const bsrAdminDb = testEnv.authenticatedContext("bsr_admin_only", { email: "filim3290@gmail.com", email_verified: true }).firestore();

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

test("Active member leaving updates memberCount, team manager can update logoUrl & delete non-main team", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("blacksea_robotics").set({
      name: "BlackSea Robotics",
      shortCode: "BSR",
      competition: "TEKNOFEST",
      schoolName: "Ünye Fen Lisesi",
      city: "Ordu",
      description: "Ana takım",
      managerName: "Platform Yöneticisi",
      managerContact: "admin@example.com",
      founderId: "bsr_founder",
      founderEmail: "admin@example.com",
      status: "approved",
      isMainTeam: true,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
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
    await db.collection("team_memberships").doc("team_fatsa_student_a").set({
      teamId: "team_fatsa",
      userId: "student_a",
      userName: "Öğrenci A",
      userEmail: "sa@example.com",
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
      email: "filim3290@gmail.com",
      role: "Yönetici",
      roles: ["Yönetici"],
      status: "active",
      isPlatformAdmin: true,
      isSuperAdmin: true
    });
  });

  const fatsaMgrDb = testEnv.authenticatedContext("fatsa_mgr", { email: "fk@example.com", email_verified: true }).firestore();
  const studentADb = testEnv.authenticatedContext("student_a", { email: "sa@example.com", email_verified: true }).firestore();
  const bsrAdminDb = testEnv.authenticatedContext("bsr_admin_only", { email: "filim3290@gmail.com", email_verified: true }).firestore();

  // Active member leaving team can sync memberCount down BEFORE deleting own membership,
  // but cannot alter name/status/logoUrl.
  await assertSucceeds(
    studentADb.collection("teams").doc("team_fatsa").update({
      memberCount: 1,
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").update({
      name: "Hacked Name",
      memberCount: 1,
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    studentADb.collection("team_memberships").doc("team_fatsa_student_a").delete()
  );

  // Team manager can update team logoUrl, student cannot
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").update({
      logoUrl: "https://example.com/bad.png",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    fatsaMgrDb.collection("teams").doc("team_fatsa").update({
      logoUrl: "https://example.com/fatsa_logo.png",
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Main blacksea_robotics team cannot be deleted even by platform admin
  await assertFails(bsrAdminDb.collection("teams").doc("blacksea_robotics").delete());

  // Fatsa team manager can delete their non-main team
  await assertSucceeds(fatsaMgrDb.collection("teams").doc("team_fatsa").delete());
});

test("Founder can suspend, mark under_review, and resume teams; blocked teams deny workspace writes while allowing appeals", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("blacksea_robotics").set({
      name: "BlackSea Robotics",
      shortCode: "BSR",
      competition: "TEKNOFEST",
      schoolName: "Ünye Fen Lisesi",
      city: "Ordu",
      description: "Ana takım",
      managerName: "Platform Yöneticisi",
      managerContact: "filim3290@gmail.com",
      founderId: "founder_u",
      founderEmail: "filim3290@gmail.com",
      status: "approved",
      isMainTeam: true,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_fatsa").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "Fatsa ekibi",
      managerName: "Fatsa Yetkili",
      managerContact: "fk@example.com",
      founderId: "fatsa_mgr",
      founderEmail: "fk@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_fatsa_fatsa_mgr").set({
      teamId: "team_fatsa",
      userId: "fatsa_mgr",
      userName: "Fatsa Yetkili",
      userEmail: "fk@example.com",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_fatsa_student_a").set({
      teamId: "team_fatsa",
      userId: "student_a",
      userName: "Öğrenci A",
      userEmail: "sa@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("blacksea_robotics_bsr_teacher").set({
      teamId: "blacksea_robotics",
      userId: "bsr_teacher",
      userName: "BSR Öğretmen",
      userEmail: "bsrteacher@example.com",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
  });

  const founderDb = testEnv.authenticatedContext("founder_u", { email: "filim3290@gmail.com", email_verified: true }).firestore();
  const bsrTeacherDb = testEnv.authenticatedContext("bsr_teacher", { email: "bsrteacher@example.com", email_verified: true }).firestore();
  const fatsaMgrDb = testEnv.authenticatedContext("fatsa_mgr", { email: "fk@example.com", email_verified: true }).firestore();
  const studentADb = testEnv.authenticatedContext("student_a", { email: "sa@example.com", email_verified: true }).firestore();

  // Main blacksea_robotics team cannot be suspended even by founder
  await assertFails(
    founderDb.collection("teams").doc("blacksea_robotics").update({
      status: "suspended",
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Non-founder Yetkili Öğretmen CANNOT suspend or place other teams under review, and cannot write app_config
  await assertFails(
    bsrTeacherDb.collection("teams").doc("team_fatsa").update({
      status: "suspended",
      moderationReason: "Yetkisiz işlem",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertFails(
    bsrTeacherDb.collection("app_config").doc("latest_release").set({ versionCode: 99 })
  );

  // Founder suspends team_fatsa with moderationReason and notifies team
  await assertSucceeds(
    founderDb.collection("teams").doc("team_fatsa").update({
      status: "suspended",
      moderationReason: "Güvenlik incelemesi sebebiyle geçici durdurma.",
      reviewedBy: "founder_u",
      appealStatus: "",
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    founderDb.collection("notifications").doc("notif_susp_1").set({
      teamId: "team_fatsa",
      recipientUserId: "all",
      type: "team_suspended",
      targetId: "team_fatsa",
      senderId: "founder_u",
      title: "Fatsa Robotik Çalışma Alanı Durduruldu",
      message: "Güvenlik incelemesi",
      isRead: false,
      createdAt: new Date(Date.now() - 1000)
    })
  );

  // Blocked team members CAN read their team document to see warning + moderationReason
  await assertSucceeds(studentADb.collection("teams").doc("team_fatsa").get());

  // Blocked team members and Yetkili Öğretmen CANNOT post messages/ideas/files/tasks/announcements
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").doc("msg_blocked").set({
      roomId: "general",
      senderId: "student_a",
      senderName: "Öğrenci A",
      text: "Durdurulmuş odada mesaj",
      isPinned: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertFails(
    fatsaMgrDb.collection("teams").doc("team_fatsa").collection("tasks").doc("task_blocked").set({
      teamId: "team_fatsa",
      title: "Bloklu görev",
      subTeam: "Yazılım Ekibi",
      createdById: "fatsa_mgr",
      createdByName: "Fatsa Yetkili",
      status: "todo",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Blocked team member/leader CANNOT self-restore status="approved", but CAN submit appeal + team_appeal alert
  await assertFails(
    fatsaMgrDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      updatedAt: new Date(Date.now() - 500)
    })
  );
  // Blocked team regular member CANNOT submit team moderation appeal or team_appeal notification
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").update({
      appealText: "Öğrenci olarak takıma itiraz göndermeye çalışıyorum.",
      appealStatus: "pending",
      appealByUserId: "student_a",
      appealByName: "Öğrenci A",
      appealSubmittedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertFails(
    studentADb.collection("notifications").doc("notif_appeal_student_denied").set({
      teamId: "platform_admin",
      recipientUserId: "platform_admins",
      type: "team_appeal",
      targetId: "team_fatsa",
      senderId: "student_a",
      title: "Yetkisiz İtiraz",
      message: "Üye itirazı reddedilmeli.",
      isRead: false,
      createdAt: new Date(Date.now() - 500)
    })
  );

  await assertSucceeds(
    fatsaMgrDb.collection("teams").doc("team_fatsa").update({
      appealText: "Eksik belgelerimizi tamamladık ve gerekli düzenlemeleri yaptık.",
      appealStatus: "pending",
      appealByUserId: "fatsa_mgr",
      appealByName: "Fatsa Yetkili",
      appealSubmittedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertSucceeds(
    fatsaMgrDb.collection("notifications").doc("notif_appeal_1").set({
      teamId: "platform_admin",
      recipientUserId: "platform_admins",
      type: "team_appeal",
      targetId: "team_fatsa",
      senderId: "fatsa_mgr",
      title: "Takım İtirazı: Fatsa Robotik",
      message: "Düzenleme yapıldı.",
      isRead: false,
      createdAt: new Date(Date.now() - 500)
    })
  );

  // Founder moves team to under_review, then resumes to approved -> workspace writes succeed again
  await assertSucceeds(
    founderDb.collection("teams").doc("team_fatsa").update({
      status: "under_review",
      moderationReason: "İtirazınız inceleniyor.",
      updatedAt: new Date(Date.now() - 300)
    })
  );
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").doc("msg_review").set({
      roomId: "general",
      senderId: "student_a",
      senderName: "Öğrenci A",
      text: "İnceleme sırasında mesaj",
      isPinned: false,
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300)
    })
  );

  await assertSucceeds(
    founderDb.collection("teams").doc("team_fatsa").update({
      status: "approved",
      moderationReason: "",
      appealText: "",
      appealStatus: "",
      appealByUserId: "",
      appealByName: "",
      updatedAt: new Date(Date.now() - 100)
    })
  );
  await assertSucceeds(
    studentADb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").doc("msg_ok").set({
      roomId: "general",
      senderId: "student_a",
      senderName: "Öğrenci A",
      text: "Takım açıldı!",
      isPinned: false,
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100)
    })
  );
});

test("Per-team member suspension with mandatory reason and member appeal flow", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const created = new Date(Date.now() - 10000);
    await db.collection("teams").doc("team_fatsa").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "Fatsa ekibi",
      managerName: "Fatsa Yetkili",
      managerContact: "fk@example.com",
      founderId: "fatsa_mgr",
      founderEmail: "fk@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_fatsa_fatsa_mgr").set({
      teamId: "team_fatsa",
      userId: "fatsa_mgr",
      userName: "Fatsa Yetkili",
      userEmail: "fk@example.com",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_fatsa_student_a").set({
      teamId: "team_fatsa",
      userId: "student_a",
      userName: "Öğrenci A",
      userEmail: "sa@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
  });

  const fatsaMgrDb = testEnv.authenticatedContext("fatsa_mgr", { email: "fk@example.com", email_verified: true }).firestore();
  const studentADb = testEnv.authenticatedContext("student_a", { email: "sa@example.com", email_verified: true }).firestore();

  // 1. Manager revokes student_a with a suspensionReason and clears appeal fields
  await assertSucceeds(
    fatsaMgrDb.collection("team_memberships").doc("team_fatsa_student_a").update({
      status: "revoked",
      isTeamAdmin: false,
      suspensionReason: "Toplantılara ve görev teslimlerine sürekli geç kalma.",
      appealText: "",
      appealStatus: "",
      updatedAt: new Date(Date.now() - 800)
    })
  );

  // 2. Suspended member CANNOT self-reset status to pending or active, and CANNOT access workspace messages
  await assertFails(
    studentADb.collection("team_memberships").doc("team_fatsa_student_a").update({
      status: "pending",
      updatedAt: new Date(Date.now() - 600)
    })
  );
  await assertFails(
    studentADb.collection("team_memberships").doc("team_fatsa_student_a").update({
      status: "active",
      updatedAt: new Date(Date.now() - 600)
    })
  );
  await assertFails(
    studentADb.collection("teams").doc("team_fatsa").collection("rooms").doc("general").collection("messages").doc("msg_revoked").set({
      roomId: "general",
      senderId: "student_a",
      senderName: "Öğrenci A",
      text: "Askıdayken mesaj",
      isPinned: false,
      createdAt: new Date(Date.now() - 600),
      updatedAt: new Date(Date.now() - 600)
    })
  );

  // 3. Suspended member CAN submit an appeal (length >= 10) while remaining revoked + notify managers
  await assertFails(
    studentADb.collection("team_memberships").doc("team_fatsa_student_a").update({
      appealText: "Kısa",
      appealStatus: "pending",
      appealSubmittedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertSucceeds(
    studentADb.collection("team_memberships").doc("team_fatsa_student_a").update({
      appealText: "Sağlık sorunum nedeniyle katılamadım, raporlarımı iletmek istiyorum.",
      appealStatus: "pending",
      appealSubmittedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertSucceeds(
    studentADb.collection("notifications").doc("notif_member_appeal_1").set({
      teamId: "team_fatsa",
      recipientUserId: "managers",
      type: "member_appeal",
      targetId: "student_a",
      senderId: "student_a",
      title: "Üye Askı İtirazı: Öğrenci A",
      message: "Sağlık sorunum nedeniyle katılamadım.",
      isRead: false,
      createdAt: new Date(Date.now() - 500)
    })
  );

  // 4. Manager CAN reject the appeal, and CAN later restore active status clearing suspension/appeal fields
  await assertSucceeds(
    fatsaMgrDb.collection("team_memberships").doc("team_fatsa_student_a").update({
      appealStatus: "rejected",
      updatedAt: new Date(Date.now() - 300)
    })
  );
  await assertSucceeds(
    fatsaMgrDb.collection("team_memberships").doc("team_fatsa_student_a").update({
      status: "active",
      suspensionReason: "",
      appealText: "",
      appealStatus: "",
      updatedAt: new Date(Date.now() - 100)
    })
  );
});

test("mehmetilkerbas@gmail.com is provisioned as BlackSea Robotics Yetkili Öğretmen only, without platform admin", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("blacksea_robotics").set({
      name: "BlackSea Robotics",
      shortCode: "BSR",
      competition: "TEKNOFEST",
      schoolName: "Ünye Fen Lisesi",
      city: "Ordu",
      description: "Ana takım",
      managerName: "Mehmet İlker Baş",
      managerContact: "mehmetilkerbas@gmail.com",
      founderId: "founder_u",
      founderEmail: "filim3290@gmail.com",
      status: "approved",
      isMainTeam: true,
      memberCount: 2,
      createdAt: now,
      updatedAt: now
    });
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
      status: "pending",
      isMainTeam: false,
      memberCount: 1,
      createdAt: now,
      updatedAt: now
    });
    await db.collection("team_memberships").doc("blacksea_robotics_bsr_captain").set({
      teamId: "blacksea_robotics",
      userId: "bsr_captain",
      userName: "BSR Kaptan",
      userEmail: "captain@example.com",
      subTeam: "Genel Yönetim",
      role: "Takım Kaptanı",
      roles: ["Takım Kaptanı"],
      status: "active",
      isTeamAdmin: true,
      createdAt: now,
      updatedAt: now
    });
  });

  const ilkerDb = testEnv.authenticatedContext("ilker_u", {
    email: "mehmetilkerbas@gmail.com",
    email_verified: true
  }).firestore();
  const captainDb = testEnv.authenticatedContext("bsr_captain", {
    email: "captain@example.com",
    email_verified: true
  }).firestore();

  // Can create own Yetkili Öğretmen profile without platform admin or 'Yönetici' role
  await assertSucceeds(
    ilkerDb.collection("users").doc("ilker_u").set({
      userId: "ilker_u",
      displayName: "Mehmet İlker Baş",
      email: "mehmetilkerbas@gmail.com",
      team: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: false,
      photoUrl: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Cannot escalate self to isPlatformAdmin or role 'Yönetici'
  await assertFails(
    ilkerDb.collection("users").doc("ilker_u").update({
      isPlatformAdmin: true,
      role: "Yönetici"
    })
  );

  // Can auto-provision active blacksea_robotics Yetkili Öğretmen membership
  await assertSucceeds(
    ilkerDb.collection("team_memberships").doc("blacksea_robotics_ilker_u").set({
      teamId: "blacksea_robotics",
      userId: "ilker_u",
      userName: "Mehmet İlker Baş",
      userEmail: "mehmetilkerbas@gmail.com",
      userPhotoUrl: "",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      joinNote: "BlackSea Robotics Yetkili Öğretmeni",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // Can create announcements inside blacksea_robotics
  await assertSucceeds(
    ilkerDb.collection("announcements").doc("bsr_ann_ilker").set({
      teamId: "blacksea_robotics",
      authorId: "ilker_u",
      authorName: "Mehmet İlker Baş",
      title: "BSR Toplantı",
      content: "Saat 16:00'da atölyedeyiz.",
      isPinned: false,
      createdAt: new Date(Date.now() - 500)
    })
  );

  // Cannot approve pending teams, write app_config, or read support tickets (not platform admin)
  await assertFails(ilkerDb.collection("teams").doc("team_fatsa").get());
  await assertFails(ilkerDb.collection("teams").doc("team_fatsa").update({ status: "approved", updatedAt: new Date() }));
  await assertFails(ilkerDb.collection("app_config").doc("latest_release").set({ versionCode: 99 }));
  await assertFails(ilkerDb.collection("support_tickets").get());

  // Other BSR captain CANNOT revoke or delete mehmetilkerbas@gmail.com's membership
  await assertFails(
    captainDb.collection("team_memberships").doc("blacksea_robotics_ilker_u").update({
      status: "revoked",
      isTeamAdmin: false,
      updatedAt: new Date(Date.now() - 100)
    })
  );
  await assertFails(
    captainDb.collection("team_memberships").doc("blacksea_robotics_ilker_u").delete()
  );
});

test("Blocked teams remain discoverable with Haber Et watch subscriptions, no join requests, and teamless support access", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("team_suspended").set({
      name: "Durdurulan Takım",
      shortCode: "DUR",
      competition: "TEKNOFEST",
      schoolName: "Ordu Fen Lisesi",
      city: "Ordu",
      description: "Geçici olarak durdurulan ekip.",
      managerName: "Yetkili",
      managerContact: "y@example.com",
      founderId: "susp_mgr",
      founderEmail: "y@example.com",
      status: "suspended",
      moderationReason: "Evrak kontrolü",
      isMainTeam: false,
      memberCount: 2,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_open").set({
      name: "Açık Takım",
      shortCode: "ACIK",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Anadolu",
      city: "Ordu",
      description: "Kullanıma açık ekip.",
      managerName: "Kaptan",
      managerContact: "k@example.com",
      founderId: "open_mgr",
      founderEmail: "k@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 3,
      createdAt: created,
      updatedAt: created
    });
  });

  const teamlessDb = testEnv.authenticatedContext("teamless_u", {
    email: "teamless@example.com",
    email_verified: true
  }).firestore();
  const founderDb = testEnv.authenticatedContext("founder_u", {
    email: "filim3290@gmail.com",
    email_verified: true
  }).firestore();

  // 1. User with 0 teams CAN discover suspended/under_review teams in directory
  await assertSucceeds(teamlessDb.collection("teams").doc("team_suspended").get());
  await assertSucceeds(
    teamlessDb.collection("teams").where("status", "in", ["approved", "suspended", "under_review"]).get()
  );

  // 2. User CANNOT apply to join a suspended team
  await assertFails(
    teamlessDb.collection("team_memberships").doc("team_suspended_teamless_u").set({
      teamId: "team_suspended",
      userId: "teamless_u",
      userName: "Takımsız Öğrenci",
      userEmail: "teamless@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "pending",
      isTeamAdmin: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );

  // 3. User CAN subscribe with Haber Et only on blocked (suspended/under_review) teams, not open teams or spoofed UIDs
  await assertFails(
    teamlessDb.collection("team_reopen_watchers").doc("team_open_teamless_u").set({
      teamId: "team_open",
      userId: "teamless_u",
      createdAt: new Date(Date.now() - 1000)
    })
  );
  await assertFails(
    teamlessDb.collection("team_reopen_watchers").doc("team_suspended_other_u").set({
      teamId: "team_suspended",
      userId: "other_u",
      createdAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    teamlessDb.collection("team_reopen_watchers").doc("team_suspended_teamless_u").set({
      teamId: "team_suspended",
      userId: "teamless_u",
      createdAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    teamlessDb.collection("team_reopen_watchers").where("userId", "==", "teamless_u").get()
  );

  // 4. Founder reopens team, reads watchers, sends team_reopened_watch notification, deletes watcher
  await assertSucceeds(
    teamlessDb.collection("support_tickets").doc("teamless_ticket_1").set({
      teamId: "blacksea_robotics",
      userId: "teamless_u",
      userName: "Takımsız Öğrenci",
      userEmail: "teamless@example.com",
      userTeam: "Takımlar Merkezi",
      category: "Genel Soru",
      subject: "Takım başvurusu hakkında",
      description: "Takımlar merkezinde desteğe ulaşabiliyorum, bilgi almak istiyorum.",
      appVersion: "1.0.15 (16)",
      deviceInfo: "Android 14",
      status: "open",
      adminReply: "",
      repliedByName: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000)
    })
  );
  await assertSucceeds(
    teamlessDb.collection("support_tickets").where("userId", "==", "teamless_u").get()
  );

  await assertSucceeds(
    founderDb.collection("team_reopen_watchers").where("teamId", "==", "team_suspended").get()
  );
  await assertSucceeds(
    founderDb.collection("teams").doc("team_suspended").update({
      status: "approved",
      moderationReason: "",
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertSucceeds(
    founderDb.collection("notifications").doc("notif_reopen_watch_1").set({
      teamId: "team_suspended",
      teamName: "Durdurulan Takım",
      recipientUserId: "teamless_u",
      type: "team_reopened_watch",
      targetId: "team_suspended",
      senderId: "founder_u",
      senderName: "BlackSea Platform Yönetimi",
      title: "Durdurulan Takım Çalışma Alanı Açıldı!",
      message: "Takım tekrar kullanıma açıldı.",
      isRead: false,
      createdAt: new Date(Date.now() - 300)
    })
  );
  await assertSucceeds(
    teamlessDb.collection("notifications").where("recipientUserId", "==", "teamless_u").get()
  );
  await assertSucceeds(
    founderDb.collection("team_reopen_watchers").doc("team_suspended_teamless_u").delete()
  );
});

test("Yetkili Öğretmen can remove active, revoked, missing/legacy, and missing-email members while blocked from protected accounts", async () => {
  const created = new Date(Date.now() - 8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("blacksea_robotics").set({
      name: "BlackSea Robotics",
      shortCode: "BSR",
      competition: "TEKNOFEST",
      schoolName: "Ünye Fen Lisesi",
      city: "Ordu",
      description: "Ana takım",
      managerName: "Mehmet İlker Baş",
      managerContact: "mehmetilkerbas@gmail.com",
      founderId: "founder_u",
      founderEmail: "filim3290@gmail.com",
      status: "approved",
      isMainTeam: true,
      memberCount: 4,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("teams").doc("team_fatsa").set({
      name: "Fatsa Robotik",
      shortCode: "FATSA",
      competition: "TEKNOFEST",
      schoolName: "Fatsa Fen Lisesi",
      city: "Ordu",
      description: "Fatsa ekibi",
      managerName: "Fatsa Öğretmen",
      managerContact: "ft@example.com",
      founderId: "fatsa_teacher",
      founderEmail: "ft@example.com",
      status: "approved",
      isMainTeam: false,
      memberCount: 3,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("users").doc("ilker_u").set({
      userId: "ilker_u",
      email: "mehmetilkerbas@gmail.com",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isSuperAdmin: true,
      isPlatformAdmin: false
    });
    await db.collection("team_memberships").doc("blacksea_robotics_ilker_u").set({
      teamId: "blacksea_robotics",
      userId: "ilker_u",
      userName: "Mehmet İlker Baş",
      userEmail: "mehmetilkerbas@gmail.com",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("blacksea_robotics_bsr_student").set({
      teamId: "blacksea_robotics",
      userId: "bsr_student",
      userName: "BSR Öğrenci",
      userEmail: "bsrstudent@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
    // Legacy user without team_memberships doc
    await db.collection("users").doc("legacy_bsr_student").set({
      userId: "legacy_bsr_student",
      email: "legacy@example.com",
      team: "Mekanik Ekibi",
      role: "Mekanik Sorumlusu",
      roles: ["Mekanik Sorumlusu"],
      status: "active",
      activeTeamId: "blacksea_robotics"
    });
    await db.collection("team_memberships").doc("team_fatsa_fatsa_teacher").set({
      teamId: "team_fatsa",
      userId: "fatsa_teacher",
      userName: "Fatsa Öğretmen",
      userEmail: "ft@example.com",
      subTeam: "Genel Yönetim",
      role: "Yetkili Öğretmen",
      roles: ["Yetkili Öğretmen"],
      status: "active",
      isTeamAdmin: true,
      createdAt: created,
      updatedAt: created
    });
    // Membership doc intentionally missing userEmail field
    await db.collection("team_memberships").doc("team_fatsa_fatsa_student").set({
      teamId: "team_fatsa",
      userId: "fatsa_student",
      userName: "Fatsa Öğrenci",
      userEmail: "",
      subTeam: "Mekanik Ekibi",
      role: "Mekanik Üyesi",
      roles: ["Mekanik Üyesi"],
      status: "revoked",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
  });

  const ilkerDb = testEnv.authenticatedContext("ilker_u", {
    email: "MehmetIlkerBas@gmail.com",
    email_verified: true
  }).firestore();
  const fatsaTeacherDb = testEnv.authenticatedContext("fatsa_teacher", {
    email: "ft@example.com",
    email_verified: true
  }).firestore();

  // 1. Reading non-existent membership doc for legacy user must succeed (resource == null)
  await assertSucceeds(ilkerDb.collection("team_memberships").doc("blacksea_robotics_legacy_bsr_student").get());

  // 2. BSR Yetkili Öğretmen can remove active membership and clear legacy user activeTeamId="" & sync count
  await assertSucceeds(ilkerDb.collection("team_memberships").doc("blacksea_robotics_bsr_student").delete());
  await assertSucceeds(
    ilkerDb.collection("users").doc("legacy_bsr_student").update({
      team: "Genel Ekip",
      role: "Takım Üyesi",
      roles: ["Takım Üyesi"],
      activeTeamId: "",
      updatedAt: new Date(Date.now() - 500)
    })
  );
  // Cannot assign arbitrary activeTeamId or role="Yönetici" on another user
  await assertFails(
    ilkerDb.collection("users").doc("legacy_bsr_student").update({
      activeTeamId: "team_fatsa",
      updatedAt: new Date(Date.now() - 400)
    })
  );
  await assertSucceeds(
    ilkerDb.collection("teams").doc("blacksea_robotics").update({
      memberCount: 1,
      updatedAt: new Date(Date.now() - 300)
    })
  );

  // 3. Standalone team Yetkili Öğretmen can delete revoked member and sync memberCount
  await assertSucceeds(fatsaTeacherDb.collection("team_memberships").doc("team_fatsa_fatsa_student").delete());
  await assertSucceeds(
    fatsaTeacherDb.collection("teams").doc("team_fatsa").update({
      memberCount: 1,
      updatedAt: new Date(Date.now() - 200)
    })
  );
});

test("Users can edit own profile (teamless or multi-team including revoked memberships) and founder can sync drifted team memberCount", async () => {
  const created = new Date(Date.now() - 8000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("teams").doc("team_alpha").set({
      name: "Alpha Robotics",
      shortCode: "ALP",
      competition: "FRC",
      schoolName: "Ordu Fen",
      city: "Ordu",
      description: "Alpha takımı",
      managerName: "Alpha Kaptan",
      managerContact: "ak@example.com",
      founderId: "alpha_mgr",
      founderEmail: "ak@example.com",
      status: "under_review",
      isMainTeam: false,
      memberCount: 4,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("users").doc("member_edit").set({
      userId: "member_edit",
      firstName: "Eski",
      lastName: "Ad",
      username: "eskiad",
      displayName: "Eski Ad",
      email: "edit@example.com",
      team: "Genel Ekip",
      role: "Takım Üyesi",
      roles: ["Takım Üyesi"],
      status: "active"
    });
    await db.collection("team_memberships").doc("team_alpha_member_edit").set({
      teamId: "team_alpha",
      userId: "member_edit",
      userName: "Eski Ad",
      userEmail: "edit@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "revoked",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
  });

  const memberDb = testEnv.authenticatedContext("member_edit", {
    email: "edit@example.com",
    email_verified: true
  }).firestore();
  const founderDb = testEnv.authenticatedContext("founder_u", {
    email: "filim3290@gmail.com",
    email_verified: true
  }).firestore();

  await assertSucceeds(
    memberDb.collection("users").doc("member_edit").update({
      firstName: "Yeni",
      lastName: "İsim",
      username: "yeniisim",
      displayName: "Yeni İsim",
      team: "Genel Ekip",
      updatedAt: new Date(Date.now() - 500)
    })
  );
  await assertSucceeds(
    memberDb.collection("team_memberships").doc("team_alpha_member_edit").update({
      userName: "Yeni İsim",
      updatedAt: new Date(Date.now() - 400)
    })
  );

  // Founder can reconcile stale memberCount even on under_review/suspended teams
  await assertSucceeds(
    founderDb.collection("teams").doc("team_alpha").update({
      memberCount: 3,
      updatedAt: new Date(Date.now() - 300)
    })
  );
});

test("User leaving BSR can reset own role/roles/isSuperAdmin to Takım Üyesi, cannot self-escalate, and founder can clean orphaned memberships", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc("bsr_leaver").set({
      userId: "bsr_leaver",
      email: "leaver@example.com",
      team: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isSuperAdmin: false,
      isPlatformAdmin: false,
      activeTeamId: "blacksea_robotics",
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("blacksea_robotics_bsr_leaver").set({
      teamId: "blacksea_robotics",
      userId: "bsr_leaver",
      userName: "Ayrılan Üye",
      userEmail: "leaver@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("users").doc("teamless_spoof").set({
      userId: "teamless_spoof",
      email: "spoof@example.com",
      team: "Genel Ekip",
      role: "Takım Üyesi",
      roles: ["Takım Üyesi"],
      status: "active",
      isSuperAdmin: false,
      isPlatformAdmin: false,
      activeTeamId: "",
      createdAt: created,
      updatedAt: created
    });
    await db.collection("team_memberships").doc("team_deleted_phantom_teamless_spoof").set({
      teamId: "team_deleted_phantom",
      userId: "teamless_spoof",
      userName: "Kullanıcı",
      userEmail: "spoof@example.com",
      subTeam: "Yazılım Ekibi",
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"],
      status: "active",
      isTeamAdmin: false,
      createdAt: created,
      updatedAt: created
    });
    await db.collection("users").doc("teamless_spoof").update({
      activeTeamId: "team_deleted_phantom"
    });
  });

  const leaverDb = testEnv.authenticatedContext("bsr_leaver", {
    email: "leaver@example.com",
    email_verified: true
  }).firestore();
  const spoofDb = testEnv.authenticatedContext("teamless_spoof", {
    email: "spoof@example.com",
    email_verified: true
  }).firestore();
  const founderDb = testEnv.authenticatedContext("founder_u", {
    email: "filim3290@gmail.com",
    email_verified: true
  }).firestore();

  // Leaving BSR deletes membership and resets user doc back to default 'Takım Üyesi'
  await assertSucceeds(leaverDb.collection("team_memberships").doc("blacksea_robotics_bsr_leaver").delete());
  await assertSucceeds(
    leaverDb.collection("users").doc("bsr_leaver").update({
      team: "Genel Ekip",
      role: "Takım Üyesi",
      roles: ["Takım Üyesi"],
      isSuperAdmin: false,
      activeTeamId: "",
      updatedAt: new Date(Date.now() - 500)
    })
  );

  // User cannot escalate own role or claim arbitrary legacy BSR role without active BSR membership
  await assertFails(
    spoofDb.collection("users").doc("teamless_spoof").update({
      role: "Takım Kaptanı",
      roles: ["Takım Kaptanı"],
      isSuperAdmin: true
    })
  );
  await assertFails(
    spoofDb.collection("users").doc("teamless_spoof").update({
      role: "Yazılım Üyesi",
      roles: ["Yazılım Üyesi"]
    })
  );

  // Founder can clean up orphaned membership for non-existent team and clear stale activeTeamId
  await assertSucceeds(
    founderDb.collection("team_memberships").doc("team_deleted_phantom_teamless_spoof").delete()
  );
  await assertSucceeds(
    founderDb.collection("users").doc("teamless_spoof").update({
      activeTeamId: "",
      updatedAt: new Date(Date.now() - 400)
    })
  );
});



