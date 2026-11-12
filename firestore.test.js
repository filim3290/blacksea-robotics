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

test("Authenticated user can send message in room", async () => {
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
