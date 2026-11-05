-- 20 seed rows for APP_USER (user-service)
-- password_hash values are real SHA-256 hex digests (64 chars), matching
-- UserServiceImpl#hashPassword's algorithm, computed from placeholder
-- passwords in the form P@ssw0rd{n}! - these are seed/demo values only.
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (91, 'James Smith', 'james.smith1@example.com', '3676ea22cb314e1c8ff8e4cb148d4f986b7f8e1a8f54f581053d853fbbb961a8', '2025-01-06 10:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (92, 'Mary Johnson', 'mary.johnson2@example.com', 'cc404b84bb10f9149d432398479ea54b1a69a43515a3db8edf3fb06b6be3203b', '2025-01-09 11:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (93, 'Robert Williams', 'robert.williams3@example.com', '9c59f5ad1bfcfe876344061b8565f34ef4412db7323ec2fd115ca1acc2b132f3', '2025-01-12 12:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (94, 'Patricia Brown', 'patricia.brown4@example.com', '38185eb9cb447b0fd0f12cf14d2aa9ab79f3dec3167c7e568cd956fba45bd68d', '2025-01-15 13:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (95, 'John Jones', 'john.jones5@example.com', '3ae322fbfeb79d5d9dc37564856510181a02010717898577e9ddde3d3e9df893', '2025-01-18 14:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (96, 'Jennifer Garcia', 'jennifer.garcia6@example.com', '192055e2dc2e6fdccc991940ef0141eeac824422485921f9d20ff03b531b0dfb', '2025-01-21 15:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (97, 'Michael Miller', 'michael.miller7@example.com', '3998dafd35d0340a6b683ba483577ff02e1082c131ca2830d44dfe4bb002012f', '2025-01-24 09:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (98, 'Linda Davis', 'linda.davis8@example.com', '41fe93cbbd6206a898a0f71ffb743d3df5845b14d8be380427a1fd6fe727c816', '2025-01-27 10:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (99, 'David Rodriguez', 'david.rodriguez9@example.com', 'be34640a4207ed0f0581fa4185db32dff79d72836d2e0e32b1868d21059bfe2f', '2025-01-30 11:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (80, 'Elizabeth Martinez', 'elizabeth.martinez10@example.com', 'dc6a15b5e246516334e1da387fe49b288af6abc1f36a1439cd02fb2f845a2a35', '2025-02-02 12:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (81, 'William Hernandez', 'william.hernandez11@example.com', '107e3d2d8e9c964ab19927fefb38977e0153d982fdb70dc2f6618017fe2856f0', '2025-02-05 13:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (82, 'Barbara Lopez', 'barbara.lopez12@example.com', 'aa8e1b55fc9c15b5648343e1b2538117b1b317a1b1b677c48ead2238a7b05d1b', '2025-02-08 14:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (83, 'Richard Gonzalez', 'richard.gonzalez13@example.com', '650a1bb8c19ee7802960a9562bff6ba14933143eb8afe7d17733b5a20c1a957d', '2025-02-11 15:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (84, 'Susan Wilson', 'susan.wilson14@example.com', 'b5e618db1e5ec579655cf1c5f5fff9c1e847c85534ba9aecf1d1346615639c23', '2025-02-14 09:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (85, 'Joseph Anderson', 'joseph.anderson15@example.com', '57dc4cb73f793a73fab0e79db50b60f91770b7770275aef5742da85e7442af6b', '2025-02-17 10:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (86, 'Jessica Thomas', 'jessica.thomas16@example.com', 'fefdb1ce5b551ce3eecedd2c4a38851cc65a23cf7cacef3ccb544002eb14833d', '2025-02-20 11:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (87, 'Thomas Taylor', 'thomas.taylor17@example.com', 'b52972e9e436775b98a7a3e53d8075ff7a9ca0efa40199168dcc6af3dd74c567', '2025-02-23 12:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (88, 'Sarah Moore', 'sarah.moore18@example.com', '0bd12c9475188c250bf26d636dada203dfd53d17897da16f9aaa17558bf973c4', '2025-02-26 13:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (89, 'Charles Jackson', 'charles.jackson19@example.com', '4be7846aed56e319dfc74b01e897c5fb69dce216b0533cb3b30ca57f2964f4fb', '2025-03-01 14:00:00');
INSERT INTO app_user (id, name, email, password_hash, created_at) VALUES (70, 'Karen Martin', 'karen.martin20@example.com', '1c062dacafa14bd96e608b983f727d60d680c3980419c6d071ee21d831769d29', '2025-03-04 15:00:00');

-- 20 seed rows for USER_PROFILE (user-service), one-to-one with APP_USER
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (91, 91, '221B Baker St, London', '555-0101', 'dark-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (92, 92, '742 Evergreen Terrace, Springfield', '555-0102', 'light-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (93, 93, '12 Grimmauld Place, London', '555-0103', 'email-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (94, 94, '4 Privet Drive, Little Whinging', '555-0104', 'sms-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (95, 95, '1600 Pennsylvania Ave, Washington', '555-0105', 'newsletter-opt-in');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (96, 96, '350 Fifth Ave, New York', '555-0106', 'newsletter-opt-out');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (97, 97, '221 Main St, Austin', '555-0107', 'dark-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (98, 98, '500 Market St, San Francisco', '555-0108', 'light-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (99, 99, '77 Massachusetts Ave, Cambridge', '555-0109', 'email-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (80, 80, '100 Michigan Ave, Chicago', '555-0110', 'sms-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (81, 81, '30 Rockefeller Plaza, New York', '555-0111', 'dark-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (82, 82, '1 Infinite Loop, Cupertino', '555-0112', 'light-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (83, 83, '233 S Wacker Dr, Chicago', '555-0113', 'email-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (84, 84, '1200 Congress Ave, Austin', '555-0114', 'sms-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (85, 85, '400 Broad St, Seattle', '555-0115', 'newsletter-opt-in');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (86, 86, '600 Congress Ave, Austin', '555-0116', 'newsletter-opt-out');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (87, 87, '55 Music Square, Nashville', '555-0117', 'dark-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (88, 88, '10 Downing St, London', '555-0118', 'light-mode');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (89, 89, '350 Rhode Island St, San Francisco', '555-0119', 'email-notifications');
INSERT INTO user_profile (id, user_id, address, phone, preferences) VALUES (70, 70, '2 Embarcadero Center, San Francisco', '555-0120', 'sms-notifications');
