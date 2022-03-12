MQTT-Test

Read data from a prova.txt file that contains some data and publish them on a MQTT broker
Then another node read the data from the broker and print in the debug console


ReadData

Read data from the prova.txt file and just print it whenever the Running node is pressed


POI-Generate-Data

Load from the POI.txt file some POI.
Generate some data with random position and noise and attach also a timestamp
Every x seconds generate the data and print them in the debug console
The message that is printed is in the form of:
msg{
	"topic":"POI",	//Used for the function do not change
	"x":3954.1835368012453,	//position of
	"y":-340.33681570145836,	//the sensor x, y
	"noise":43.80376571322864,	//random noise value 
	"timestamp":1647093192485,"	//timestamp
	_msgid":"75d0f1cf996258f2",	//automatic id generator
	"POI":			//Point of interested loaded from file, 
	{			//nearest from the sensor
		"Name":"Porta Pia",
		"x":4000,
		"y":-2000
	}

}