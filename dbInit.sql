DROP DATABASE IF EXISTS meshservice;
CREATE DATABASE meshservice;

USE meshservice;

CREATE TABLE users (
	id INT AUTO_INCREMENT PRIMARY KEY,
	username VARCHAR(255) NOT NULL UNIQUE,
	password VARCHAR(255) NOT NULL
);

CREATE TABLE posts (
	id INT AUTO_INCREMENT PRIMARY KEY,
	ownerID INT NOT NULL,
	content TEXT NOT NULL,
	created timestamp NOT NULL DEFAULT current_timestamp(),
	FOREIGN KEY (ownerID) REFERENCES users(id)
);