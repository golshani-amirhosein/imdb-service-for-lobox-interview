# IMDb Service
This service is a Java backend with Spring Boot and loads the IMDB dataset into memory using hash-set for specific queries.

To use:

1. Clone.
2. Open the `application.properties` file and set the `dataset.path` value to the `folder full-path` where the dataset files with the extension `tsv` or `tsv.gz` are located. (This server can read both extensions)
3. Run.
4. Open the address `http://localhost:8080` with a browser.
5. Click `Check the Requirements` to make sure that the folder settings are correct.
6. Click `Import` to load the dataset into memory. You will need about 5 GB of RAM to import.
7. It will take about 2-3 minutes for the entire process to complete. The process is visible both visually in the browser and in the server console.
8. When it is done, you will see the `<< READY >>` message.
9. The rest of the items on the index page were the same as what was requested in the email.

I hope it was what you had in mind.
