$( document ).ready(function() {
  displayLatestWarning();
});

$(window).on('hashchange', function(){
  displayLatestWarning();
});

function displayLatestWarning(){
  $( ".latest-warning" ).css( "display", $(location).attr('href').includes(latestWarningTrigger)?"inherit":"none" );
}