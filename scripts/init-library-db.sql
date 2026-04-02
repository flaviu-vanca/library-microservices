-- =============================================================
-- Library Service Database Initialization
-- =============================================================

CREATE DATABASE IF NOT EXISTS library_db;
USE library_db;

-- Libraries table
CREATE TABLE IF NOT EXISTS libraries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    country VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Books table
CREATE TABLE IF NOT EXISTS books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    publication_year INT,
    genre VARCHAR(50),
    library_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (library_id) REFERENCES libraries(id) ON DELETE SET NULL,
    INDEX idx_isbn (isbn),
    INDEX idx_library (library_id)
);

-- Sample data for demo
-- Idempotent inserts so this script can be re-applied safely to an existing database.
INSERT INTO libraries (name, address, city, country)
SELECT seed.name, seed.address, seed.city, seed.country
FROM (
    SELECT 'TUS Moylish Library' AS name, 'Moylish Park' AS address, 'Limerick' AS city, 'Ireland' AS country
    UNION ALL
    SELECT 'TUS Athlone Library', 'Dublin Road', 'Athlone', 'Ireland'
    UNION ALL
    SELECT 'Dublin City Library', 'Pearse Street', 'Dublin', 'Ireland'
    UNION ALL
    SELECT 'Dublin Central Library', 'Parnell Square', 'Dublin', 'Ireland'
) seed
LEFT JOIN libraries existing ON existing.name = seed.name
WHERE existing.id IS NULL;

INSERT INTO books (isbn, title, author, publication_year, genre, library_id)
SELECT seed.isbn, seed.title, seed.author, seed.publication_year, seed.genre, library.id
FROM (
    SELECT '978-0-13-468599-1' AS isbn, 'Clean Code' AS title, 'Robert C. Martin' AS author, 2008 AS publication_year, 'Technology' AS genre, 'TUS Moylish Library' AS library_name
    UNION ALL SELECT '978-0-59-651798-1', 'JavaScript: The Good Parts', 'Douglas Crockford', 2008, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-0-13-235088-4', 'Clean Architecture', 'Robert C. Martin', 2017, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-001-1', 'Designing Data-Intensive Applications', 'Martin Kleppmann', 2017, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-002-1', 'Refactoring', 'Martin Fowler', 2018, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-003-1', 'The Pragmatic Programmer', 'Andrew Hunt', 1999, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-004-1', 'Introduction to Algorithms', 'Thomas H. Cormen', 2009, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-005-1', 'Kubernetes: Up and Running', 'Brendan Burns', 2022, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-006-1', 'Spring in Action', 'Craig Walls', 2022, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-007-1', 'Effective Java', 'Joshua Bloch', 2018, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-008-1', 'Head First Design Patterns', 'Eric Freeman', 2020, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-009-1', 'Site Reliability Engineering', 'Betsy Beyer', 2016, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-010-1', 'Accelerate', 'Nicole Forsgren', 2018, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-011-1', 'System Design Interview', 'Alex Xu', 2020, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-012-1', 'Working Effectively with Legacy Code', 'Michael Feathers', 2004, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-013-1', 'Continuous Delivery', 'Jez Humble', 2010, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-014-1', 'Fundamentals of Software Architecture', 'Mark Richards', 2020, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-015-1', 'Release It!', 'Michael T. Nygard', 2018, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-016-1', 'Software Engineering at Google', 'Titus Winters', 2020, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-017-1', 'Database Internals', 'Alex Petrov', 2019, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-018-1', 'Grokking Algorithms', 'Aditya Bhargava', 2016, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-019-1', 'Building Event-Driven Microservices', 'Adam Bellemare', 2020, 'Technology', 'TUS Moylish Library'
    UNION ALL SELECT '978-1-10000-020-1', 'Microservices Patterns', 'Chris Richardson', 2018, 'Technology', 'TUS Moylish Library'

    UNION ALL SELECT '978-0-32-112521-7', 'Domain-Driven Design', 'Eric Evans', 2003, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-49-195017-1', 'Building Microservices', 'Sam Newman', 2021, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-001-1', 'Designing Distributed Systems', 'Brendan Burns', 2018, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-002-1', 'The Phoenix Project', 'Gene Kim', 2018, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-003-1', 'The DevOps Handbook', 'Gene Kim', 2021, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-004-1', 'Cloud Native Patterns', 'Cornelia Davis', 2019, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-005-1', 'Terraform: Up and Running', 'Yevgeniy Brikman', 2022, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-006-1', 'Docker Deep Dive', 'Nigel Poulton', 2023, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-007-1', 'Streaming Systems', 'Tyler Akidau', 2018, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-008-1', 'Kafka: The Definitive Guide', 'Gwen Shapira', 2021, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-009-1', 'Designing Machine Learning Systems', 'Chip Huyen', 2022, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-010-1', 'Data Mesh', 'Zhamak Dehghani', 2022, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-011-1', 'Observability Engineering', 'Charity Majors', 2022, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-012-1', 'Prometheus: Up and Running', 'Brian Brazil', 2021, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-013-1', 'Learning SQL', 'Alan Beaulieu', 2020, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-014-1', 'Fundamentals of Data Engineering', 'Joe Reis', 2022, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-015-1', 'Real-Time Microservices', 'Jonas Boner', 2019, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-016-1', 'API Design Patterns', 'JJ Geewax', 2021, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-017-1', 'Enterprise Integration Patterns', 'Gregor Hohpe', 2003, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-018-1', 'Security Engineering', 'Ross Anderson', 2020, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-019-1', 'Practical Monitoring', 'Mike Julian', 2018, 'Technology', 'TUS Athlone Library'
    UNION ALL SELECT '978-1-20000-020-1', 'Building Secure and Reliable Systems', 'Heather Adkins', 2020, 'Technology', 'TUS Athlone Library'

    UNION ALL SELECT '978-1-30000-001-1', 'Sapiens', 'Yuval Noah Harari', 2015, 'History', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-002-1', 'Educated', 'Tara Westover', 2018, 'Memoir', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-003-1', 'The Night Circus', 'Erin Morgenstern', 2011, 'Fantasy', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-004-1', 'Klara and the Sun', 'Kazuo Ishiguro', 2021, 'Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-005-1', 'The Book Thief', 'Markus Zusak', 2005, 'Historical Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-006-1', 'The Midnight Library', 'Matt Haig', 2020, 'Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-007-1', 'Project Hail Mary', 'Andy Weir', 2021, 'Science Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-008-1', 'Atomic Habits', 'James Clear', 2018, 'Self-Help', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-009-1', 'Thinking, Fast and Slow', 'Daniel Kahneman', 2011, 'Psychology', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-010-1', 'A Brief History of Time', 'Stephen Hawking', 1988, 'Science', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-011-1', 'The Silent Patient', 'Alex Michaelides', 2019, 'Thriller', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-012-1', 'Normal People', 'Sally Rooney', 2018, 'Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-013-1', 'The Martian', 'Andy Weir', 2014, 'Science Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-014-1', 'Circe', 'Madeline Miller', 2018, 'Fantasy', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-015-1', 'Where the Crawdads Sing', 'Delia Owens', 2018, 'Mystery', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-016-1', 'The Vanishing Half', 'Brit Bennett', 2020, 'Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-017-1', 'The Seven Husbands of Evelyn Hugo', 'Taylor Jenkins Reid', 2017, 'Fiction', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-018-1', 'Pride and Prejudice', 'Jane Austen', 1813, 'Classic', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-019-1', '1984', 'George Orwell', 1949, 'Classic', 'Dublin City Library'
    UNION ALL SELECT '978-1-30000-020-1', 'The Hobbit', 'J.R.R. Tolkien', 1937, 'Fantasy', 'Dublin City Library'

    UNION ALL SELECT '978-1-50000-001-1', 'Ulysses', 'James Joyce', 1922, 'Classic', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-002-1', 'Dubliners', 'James Joyce', 1914, 'Classic', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-003-1', 'The Picture of Dorian Gray', 'Oscar Wilde', 1890, 'Classic', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-004-1', 'Dracula', 'Bram Stoker', 1897, 'Classic', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-005-1', 'Small Things Like These', 'Claire Keegan', 2021, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-006-1', 'Brooklyn', 'Colm Toibin', 2009, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-007-1', 'The Heart''s Invisible Furies', 'John Boyne', 2017, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-008-1', 'The Bee Sting', 'Paul Murray', 2023, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-009-1', 'Room', 'Emma Donoghue', 2010, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-010-1', 'The Commitments', 'Roddy Doyle', 1987, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-011-1', 'Solar Bones', 'Mike McCormack', 2016, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-012-1', 'Say Nothing', 'Patrick Radden Keefe', 2019, 'History', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-013-1', 'Empire of Pain', 'Patrick Radden Keefe', 2021, 'History', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-014-1', 'The Thursday Murder Club', 'Richard Osman', 2020, 'Mystery', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-015-1', 'Lessons in Chemistry', 'Bonnie Garmus', 2022, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-016-1', 'Tomorrow, and Tomorrow, and Tomorrow', 'Gabrielle Zevin', 2022, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-017-1', 'Demon Copperhead', 'Barbara Kingsolver', 2022, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-018-1', 'Yellowface', 'R.F. Kuang', 2023, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-019-1', 'Remarkably Bright Creatures', 'Shelby Van Pelt', 2022, 'Fiction', 'Dublin Central Library'
    UNION ALL SELECT '978-1-50000-020-1', 'The Wager', 'David Grann', 2023, 'History', 'Dublin Central Library'
) seed
JOIN libraries library ON library.name = seed.library_name
LEFT JOIN books existing ON existing.isbn = seed.isbn
WHERE existing.id IS NULL;
