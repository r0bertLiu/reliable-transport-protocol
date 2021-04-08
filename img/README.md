# Country Index
> Junior iOS Assignment from AUTOMIC GROUP

In this project user is able to search a country by it name of ISOCode, the data of countries is fetch from public endpoint.

## Usage
1. Open the app from xcode, the development environment of xcode is Version 12.4
2. Seletct the build target to a ios simulator
3. Build the application 
4. In the shown interface of application click all to show the all countries data in a pagination scroll view
5. Type the country's name of ISOCode in text input field and click search button to found the country. 

## Design Note : 
The application is designed by a simple MVC architecture with some helper functions class.

### Model
There are two Data model in this appliaction: Country and CountriesFilter

#### Country model
The country model is used a data model to represent the country's infomation. This class is design to be a NScoding object with encode and decode functions for userdefault local storage purpose.

It also have a class function to populate a list of data into array of Country instance and a equal operation for testing.

#### CountriesFilter
This model is used as the data source of the tableView which will shown the searching result in application. It store an array of the total countries information data and use regular expression to filter the seaching result form user input.  

The filter function is done by match the prefix, if user provide a correct name of country it will match the unique country information
```
e.g "AU" will match show the country infomation for both Australia and Austria
```

### view
A simple UITableCell view to render the information list for each country by given data model

### countroller
The viewcountroller will first try to get the countries data locally, if failed it will fetch the API endpoint to get the data. After that, it will create the CountriesFilter and use it to render the tableview and show the search result based on the user behaviours.

### helper function class
There are two helper function class, ApiManager and LocalManager, both of them are singleton class. The ApiManager is used for fetching the api and LocalManager is used for get and set the countries model from UserDefaults. and decode, encode the model into data 

## Testing
Testing is mainly foucs on test the two data model and their class and object function do running in correct way.


## Improvement
There are 3 considerable improvements

1. A better user interface to show the countries data in more user friendly way
2. For locally storage consider to use the core data, as userDefault is mainly used to store the user's data and core data is better to deal with large amount of data
3. The textInput field could be write into a custorm class of view to reduce redundant